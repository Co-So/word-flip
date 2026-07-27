import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { CardMedia, ImageTransform, MediaCard, MediaRepository } from "@/domain/media";

const EMPTY_TRANSFORM: ImageTransform = {
  rotation: 0,
  scale: 1,
  positionX: 0,
  positionY: 0
};

function notFound(message: string): AppError {
  return { kind: "not-found", message };
}

function validation(message: string): AppError {
  return { kind: "validation", message, fieldErrors: {} };
}

function validTransform(transform: ImageTransform): boolean {
  return (
    [0, 90, 180, 270].includes(transform.rotation) &&
    Number.isFinite(transform.scale) &&
    transform.scale >= 0.5 &&
    transform.scale <= 2 &&
    Number.isFinite(transform.positionX) &&
    Number.isFinite(transform.positionY)
  );
}

/** 所有媒体读写都先在当前计划的 cardId 索引中验权，wordKey 不参与持久化定位。 */
export class MockMediaRepository implements MediaRepository {
  constructor(private readonly store: DemoStateStore) {}

  listCards(): Promise<MediaCard[]> {
    const plan = this.store.readActivePlanState();
    if (!plan) {
      return Promise.reject(notFound("当前没有可用学习计划"));
    }
    return Promise.resolve(
      Object.values(plan.cards.byCardId).map((card) => ({
        cardId: card.cardId,
        headword: card.headword,
        definition: card.definition,
        media: structuredClone(plan.media.byCardId[card.cardId])
      }))
    );
  }

  getMedia(cardId: string): Promise<CardMedia> {
    const media = this.store.readActivePlanState()?.media.byCardId[cardId];
    return media
      ? Promise.resolve(structuredClone(media))
      : Promise.reject(notFound("找不到当前计划的卡片媒体"));
  }

  saveTransform(cardId: string, transform: ImageTransform): Promise<CardMedia> {
    if (!validTransform(transform)) {
      return Promise.reject(validation("图片位置参数无效"));
    }
    try {
      this.updateCurrent(cardId, (media) => {
        media.transform = structuredClone(transform);
      });
      return this.getMedia(cardId);
    } catch (error) {
      return Promise.reject(error);
    }
  }

  saveUploadedImage(
    cardId: string,
    fixedAssetPath: string,
    transform: ImageTransform
  ): Promise<CardMedia> {
    if (fixedAssetPath !== "/card-images/custom-placeholder.webp" || !validTransform(transform)) {
      return Promise.reject(validation("上传图片映射或位置参数无效"));
    }
    try {
      this.updateCurrent(cardId, (media) => {
        // 模拟上传只保存安全项目资产路径，临时 blob URL 与文件内容永不进入状态。
        media.imageUrl = fixedAssetPath;
        media.transform = structuredClone(transform);
      });
      return this.getMedia(cardId);
    } catch (error) {
      return Promise.reject(error);
    }
  }

  clearImage(cardId: string): Promise<CardMedia> {
    try {
      this.updateCurrent(cardId, (media) => {
        media.imageUrl = null;
        media.transform = structuredClone(EMPTY_TRANSFORM);
      });
      return this.getMedia(cardId);
    } catch (error) {
      return Promise.reject(error);
    }
  }

  private updateCurrent(cardId: string, mutate: (media: CardMedia) => void): void {
    const plan = this.store.readActivePlanState();
    if (!plan?.cards.byCardId[cardId] || !plan.media.byCardId[cardId]) {
      throw notFound("找不到当前计划的卡片媒体");
    }
    this.store.updateActivePlan((draft) => {
      mutate(draft.media.byCardId[cardId]);
    });
  }
}
