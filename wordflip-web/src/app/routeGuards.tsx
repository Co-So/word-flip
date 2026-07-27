import { useEffect, useState, type PropsWithChildren } from "react";
import { Navigate } from "react-router-dom";
import { useRepositories } from "@/data/runtime/RepositoryContext";

function LoadingGuard() {
  return <div role="status">正在确认访问权限</div>;
}

/** 未登录时只允许访问认证页面，避免受保护页面短暂暴露。 */
export function RequireAuth({ children }: PropsWithChildren) {
  const { auth } = useRepositories();
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);

  useEffect(() => {
    let active = true;
    auth.getSession().then((session) => {
      if (active) setAuthenticated(session?.authenticated === true);
    });
    return () => { active = false; };
  }, [auth]);

  if (authenticated === null) return <LoadingGuard />;
  return authenticated ? children : <Navigate replace to="/login" />;
}

/** 已登录但尚未创建当前学习计划时，必须先经过首次设置。 */
export function RequireOnboarding({ children }: PropsWithChildren) {
  const { books } = useRepositories();
  const [hasPlan, setHasPlan] = useState<boolean | null>(null);

  useEffect(() => {
    let active = true;
    books.getActivePlan().then((plan) => {
      if (active) setHasPlan(plan !== null);
    });
    return () => { active = false; };
  }, [books]);

  if (hasPlan === null) return <LoadingGuard />;
  return hasPlan ? children : <Navigate replace to="/onboarding" />;
}
