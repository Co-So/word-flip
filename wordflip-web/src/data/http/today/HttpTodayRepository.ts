import type { AxiosInstance } from "axios";
import type { TodayDashboardDto } from "@/data/http/today/todayDtos";
import { mapTodaySummary } from "@/data/http/today/todayMappers";
import type { TodayRepository, TodaySummary } from "@/domain/today";

/** 使用共享认证客户端读取服务端权威的今日任务快照。 */
export class HttpTodayRepository implements TodayRepository {
  constructor(
    private readonly client: AxiosInstance,
    private readonly getTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone
  ) {}

  async getSummary(): Promise<TodaySummary> {
    const response = await this.client.get<TodayDashboardDto>("/today", {
      headers: { "X-Timezone": this.getTimeZone() || "UTC" }
    });
    return mapTodaySummary(response.data);
  }
}
