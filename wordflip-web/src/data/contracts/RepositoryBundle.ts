import type { AuthRepository } from "@/domain/auth";
import type { BookRepository } from "@/domain/books";
import type { GroupRepository } from "@/domain/groups";
import type { StudyRepository } from "@/domain/learning";
import type { MediaRepository } from "@/domain/media";
import type { QuizRepository } from "@/domain/quiz";
import type { SettingsRepository } from "@/domain/settings";
import type { StatsRepository } from "@/domain/stats";
import type { TodayRepository } from "@/domain/today";

export interface RepositoryBundle {
  auth: AuthRepository;
  settings: SettingsRepository;
  books: BookRepository;
  groups: GroupRepository;
  today: TodayRepository;
  study: StudyRepository;
  quiz: QuizRepository;
  media: MediaRepository;
  stats: StatsRepository;
}
