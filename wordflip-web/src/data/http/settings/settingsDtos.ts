export type GroupSizeDto = 10 | 20 | 30 | 50;
export type GroupStrategyDto = "book_order" | "frequency" | "random";

export interface UserSettingsDto {
  activePlanId: number | null;
  groupSize: GroupSizeDto;
  groupStrategy: GroupStrategyDto;
  autoSpeak: boolean;
  themeMode: "system" | "light" | "dark";
  heatDisplayMode?: "combined" | "dictation" | "choice" | "free";
  quizLaunchMode?: "mixed" | "free_select";
  defaultQuestionLimit?: number;
}

export interface PreferencesPatchDto {
  autoSpeak?: boolean;
  groupSize?: GroupSizeDto;
  groupStrategy?: GroupStrategyDto;
}
