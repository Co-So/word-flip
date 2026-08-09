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
  const [planState, setPlanState] = useState<"loading" | "ready" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setPlanState("loading");
    books.getActivePlan()
      .then((plan) => {
        if (active) setPlanState(plan === null ? "missing" : "ready");
      })
      .catch(() => {
        // 接口不可用时必须显式失败，不能永久停留在加载态或回退到模拟数据。
        if (active) setPlanState("error");
      });
    return () => { active = false; };
  }, [attempt, books]);

  if (planState === "loading") return <LoadingGuard />;
  if (planState === "error") {
    return (
      <div role="alert">
        <p>无法确认当前学习计划，请检查网络后重试。</p>
        <button type="button" onClick={() => setAttempt((value) => value + 1)}>重试</button>
      </div>
    );
  }
  return planState === "ready" ? children : <Navigate replace to="/onboarding" />;
}
