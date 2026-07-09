package com.wordflip.service;

import com.wordflip.domain.StudyGroup;
import com.wordflip.domain.UserRecentGroup;
import com.wordflip.dto.today.RecentGroupDto;
import com.wordflip.dto.today.RecommendedStudy;
import com.wordflip.dto.today.StudyReason;
import com.wordflip.dto.today.TaskSource;
import com.wordflip.dto.today.TodayDashboard;
import com.wordflip.dto.today.TodayStats;
import com.wordflip.dto.today.TodayTask;
import com.wordflip.dto.today.TodayTasks;
import com.wordflip.repository.GroupRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.repository.TodayQueryRepository;
import com.wordflip.repository.UserRecentGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GET /today 业务编排：统计、任务、推荐分组、最近组（REQ-TODAY-3~12）。
 */
@Service
public class TodayService {

    private final TodayQueryRepository todayQueryRepository;
    private final GroupRepository groupRepository;
    private final GroupWordRepository groupWordRepository;
    private final UserRecentGroupRepository userRecentGroupRepository;
    private final ReviewService reviewService;
    private final TodayCacheService todayCacheService;

    public TodayService(
            TodayQueryRepository todayQueryRepository,
            GroupRepository groupRepository,
            GroupWordRepository groupWordRepository,
            UserRecentGroupRepository userRecentGroupRepository,
            ReviewService reviewService,
            TodayCacheService todayCacheService
    ) {
        this.todayQueryRepository = todayQueryRepository;
        this.groupRepository = groupRepository;
        this.groupWordRepository = groupWordRepository;
        this.userRecentGroupRepository = userRecentGroupRepository;
        this.reviewService = reviewService;
        this.todayCacheService = todayCacheService;
    }

    @Transactional(readOnly = true)
    public TodayDashboard getDashboard(Long userId, ZoneId zoneId) {
        LocalDate today = com.wordflip.util.UserTimeZoneUtil.todayInZone(zoneId);
        return todayCacheService.get(userId, today)
                .orElseGet(() -> {
                    TodayDashboard dashboard = buildDashboard(userId, today);
                    todayCacheService.put(userId, today, dashboard);
                    return dashboard;
                });
    }

    TodayDashboard buildDashboard(Long userId, LocalDate today) {
        long totalAssigned = todayQueryRepository.countAssignedWords(userId);
        long masteredCount = todayQueryRepository.countMastered(userId, today);
        long dueReviewCount = todayQueryRepository.countDueReview(userId, today);
        long newWordsCount = todayQueryRepository.countNewWords(userId);
        long quizCount = todayQueryRepository.countQuizPool(userId, today);

        int completionPercent = totalAssigned == 0
                ? 0
                : Math.round((float) masteredCount / totalAssigned * 100f);

        List<StudyGroup> groups = groupRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<TaskSource> newWordSources = new ArrayList<>();
        List<TaskSource> dueReviewSources = new ArrayList<>();

        for (StudyGroup group : groups) {
            long groupNew = todayQueryRepository.countNewWordsInGroup(userId, group.getId());
            if (groupNew > 0) {
                newWordSources.add(new TaskSource(group.getId(), group.getName(), (int) groupNew));
            }
            long groupDue = todayQueryRepository.countDueReviewInGroup(userId, group.getId(), today);
            if (groupDue > 0) {
                dueReviewSources.add(new TaskSource(group.getId(), group.getName(), (int) groupDue));
            }
        }

        newWordSources.sort(Comparator.comparingInt(TaskSource::count).reversed());
        dueReviewSources.sort(Comparator.comparingInt(TaskSource::count).reversed());

        TodayStats stats = new TodayStats((int) masteredCount, (int) dueReviewCount, completionPercent);
        TodayTasks tasks = new TodayTasks(
                TodayTask.of((int) newWordsCount, "新词学习", newWordSources),
                TodayTask.of((int) dueReviewCount, "到期复习", dueReviewSources),
                TodayTask.of((int) quizCount, "默写测验")
        );

        int streakDays = reviewService.calculateStreakDays(userId, today);
        RecommendedStudy recommended = pickRecommendedStudy(groups, newWordSources, dueReviewSources);
        List<RecentGroupDto> recentGroups = loadRecentGroups(userId, groups);

        return new TodayDashboard(today, streakDays, stats, tasks, recommended, recentGroups);
    }

    /** 最近学习/测验分组，最多 3 条 */
    private List<RecentGroupDto> loadRecentGroups(Long userId, List<StudyGroup> groups) {
        Map<Long, StudyGroup> byId = groups.stream()
                .collect(Collectors.toMap(StudyGroup::getId, Function.identity(), (a, b) -> a));
        List<UserRecentGroup> recent = userRecentGroupRepository.findRecentByUserId(userId);
        List<RecentGroupDto> result = new ArrayList<>();
        for (UserRecentGroup item : recent) {
            StudyGroup group = byId.get(item.getGroupId());
            if (group == null) {
                group = groupRepository.findByIdAndUserId(item.getGroupId(), userId).orElse(null);
            }
            if (group == null) {
                continue;
            }
            result.add(new RecentGroupDto(group.getId(), group.getName(), item.getLastStudiedAt()));
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    /** 优先新词最多组，其次到期复习；混合时 reason=mixed */
    private RecommendedStudy pickRecommendedStudy(
            List<StudyGroup> groups,
            List<TaskSource> newWordSources,
            List<TaskSource> dueReviewSources
    ) {
        if (groups.isEmpty()) {
            return null;
        }
        TaskSource topNew = newWordSources.isEmpty() ? null : newWordSources.getFirst();
        TaskSource topDue = dueReviewSources.isEmpty() ? null : dueReviewSources.getFirst();

        if (topNew != null && (topDue == null || topNew.count() >= topDue.count())) {
            StudyReason reason = topDue != null && topDue.count() > 0 ? StudyReason.mixed : StudyReason.new_words;
            if (topDue != null && topDue.groupId() == topNew.groupId() && topDue.count() > 0) {
                reason = StudyReason.mixed;
            }
            return new RecommendedStudy(topNew.groupId(), topNew.groupName(), topNew.count(), reason);
        }
        if (topDue != null) {
            StudyReason reason = topNew != null && topNew.count() > 0 ? StudyReason.mixed : StudyReason.due_review;
            return new RecommendedStudy(topDue.groupId(), topDue.groupName(), topDue.count(), reason);
        }
        StudyGroup first = groups.getFirst();
        int wordCount = (int) groupWordRepository.countByGroupId(first.getId());
        if (wordCount <= 0) {
            wordCount = 1;
        }
        return new RecommendedStudy(first.getId(), first.getName(), wordCount, StudyReason.new_words);
    }
}
