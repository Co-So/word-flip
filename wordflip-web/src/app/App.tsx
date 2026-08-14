import { Navigate, useRoutes } from "react-router-dom";
import { RequireAuth, RequireOnboarding } from "@/app/routeGuards";
import { LoginPage } from "@/features/auth/LoginPage";
import { RegisterPage } from "@/features/auth/RegisterPage";
import { OnboardingPage } from "@/features/onboarding/OnboardingPage";
import { TodayPage } from "@/features/today/TodayPage";
import { BooksPage } from "@/features/books/BooksPage";
import { BookDetailPage } from "@/features/books/BookDetailPage";
import { GroupsPage } from "@/features/groups/GroupsPage";
import { GroupDetailPage } from "@/features/groups/GroupDetailPage";
import { CustomGroupPage } from "@/features/groups/CustomGroupPage";
import { StudyCompletePage } from "@/features/study/StudyCompletePage";
import { StudyPage } from "@/features/study/StudyPage";
import { QuizPage } from "@/features/quiz/QuizPage";
import { QuizResultPage } from "@/features/quiz/QuizResultPage";
import { QuizSetupPage } from "@/features/quiz/QuizSetupPage";
import { MediaPage } from "@/features/media/MediaPage";
import { StatsPage } from "@/features/stats/StatsPage";
import { SettingsPage } from "@/features/settings/SettingsPage";
import { AppShell } from "@/layouts/AppShell/AppShell";

export function App() {
  return useRoutes([
    { path: "/login", element: <LoginPage /> },
    { path: "/register", element: <RegisterPage /> },
    { path: "/onboarding", element: <RequireAuth><OnboardingPage /></RequireAuth> },
    {
      element: <RequireAuth><RequireOnboarding><AppShell /></RequireOnboarding></RequireAuth>,
      children: [
        { path: "/today", element: <TodayPage /> },
        { path: "/books", element: <BooksPage /> },
        { path: "/books/:bookId", element: <BookDetailPage /> },
        { path: "/groups", element: <GroupsPage /> },
        { path: "/groups/new", element: <CustomGroupPage /> },
        { path: "/groups/:groupId", element: <GroupDetailPage /> },
        { path: "/quiz", element: <QuizSetupPage /> },
        { path: "/media", element: <MediaPage /> },
        { path: "/stats", element: <StatsPage /> },
        { path: "/settings", element: <SettingsPage /> }
      ]
    },
    {
      path: "/study/:sessionId",
      element: <RequireAuth><RequireOnboarding><StudyPage /></RequireOnboarding></RequireAuth>
    },
    {
      path: "/study/:sessionId/complete",
      element: <RequireAuth><RequireOnboarding><StudyCompletePage /></RequireOnboarding></RequireAuth>
    },
    {
      path: "/quiz/:sessionId",
      element: <RequireAuth><RequireOnboarding><QuizPage /></RequireOnboarding></RequireAuth>
    },
    {
      path: "/quiz/:sessionId/result",
      element: <RequireAuth><RequireOnboarding><QuizResultPage /></RequireOnboarding></RequireAuth>
    },
    { path: "*", element: <Navigate replace to="/today" /> }
  ]);
}
