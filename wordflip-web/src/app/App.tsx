import { Navigate, useRoutes } from "react-router-dom";
import { RequireAuth, RequireOnboarding } from "@/app/routeGuards";
import { LoginPage } from "@/features/auth/LoginPage";
import { RegisterPage } from "@/features/auth/RegisterPage";
import { OnboardingPage } from "@/features/onboarding/OnboardingPage";

function TodayPlaceholder() {
  return <main><h1>今天继续前进</h1><p>学习计划已准备好。</p></main>;
}

export function App() {
  return useRoutes([
    { path: "/login", element: <LoginPage /> },
    { path: "/register", element: <RegisterPage /> },
    { path: "/onboarding", element: <RequireAuth><OnboardingPage /></RequireAuth> },
    { path: "/today", element: <RequireAuth><RequireOnboarding><TodayPlaceholder /></RequireOnboarding></RequireAuth> },
    { path: "*", element: <Navigate replace to="/today" /> }
  ]);
}
