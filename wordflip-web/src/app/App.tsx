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
import { StudyCompletePage } from "@/features/study/StudyCompletePage";
import { StudyPage } from "@/features/study/StudyPage";
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
        { path: "/groups/:groupId", element: <GroupDetailPage /> }
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
    { path: "*", element: <Navigate replace to="/today" /> }
  ]);
}
