import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Panel } from "@/components/Panel/Panel";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { ImageTransform, MediaCard } from "@/domain/media";
import { ImageEditor } from "./ImageEditor";
import styles from "./media.module.css";

const MAX_FILE_SIZE = 5 * 1024 * 1024;
const ACCEPTED_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : "暂时无法读取卡片媒体";
}

export function MediaPage() {
  const { media } = useRepositories();
  const [cards, setCards] = useState<MediaCard[]>([]);
  const [selectedCardId, setSelectedCardId] = useState("");
  const [transform, setTransform] = useState<ImageTransform>({
    rotation: 0,
    scale: 1,
    positionX: 0,
    positionY: 0
  });
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const previewUrlRef = useRef<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [previewName, setPreviewName] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [status, setStatus] = useState<"loading" | "empty" | "error" | "ready">("loading");
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    setStatus("loading");
    try {
      const items = await media.listCards();
      setCards(items);
      setSelectedCardId((current) =>
        items.some((item) => item.cardId === current) ? current : items[0]?.cardId ?? ""
      );
      setStatus(items.length ? "ready" : "empty");
    } catch (reason) {
      setError(messageOf(reason));
      setStatus("error");
    }
  }, [media]);

  useEffect(() => {
    void load();
  }, [load]);

  const selected = useMemo(
    () => cards.find((card) => card.cardId === selectedCardId) ?? null,
    [cards, selectedCardId]
  );

  useEffect(() => {
    if (selected) {
      setTransform(structuredClone(selected.media.transform));
    }
  }, [selected]);

  const releasePreview = useCallback(() => {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = null;
    }
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
    setPreviewUrl(null);
    setPreviewName("");
  }, []);

  useEffect(() => () => {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = null;
    }
  }, []);

  function selectCard(cardId: string) {
    releasePreview();
    setFieldError(null);
    setSelectedCardId(cardId);
  }

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setFieldError(null);
    if (!ACCEPTED_TYPES.has(file.type)) {
      setFieldError("仅支持 JPEG、PNG 或 WebP");
      event.target.value = "";
      return;
    }
    if (file.size > MAX_FILE_SIZE) {
      setFieldError("图片不能超过 5MB");
      event.target.value = "";
      return;
    }
    releasePreview();
    const objectUrl = URL.createObjectURL(file);
    previewUrlRef.current = objectUrl;
    setPreviewUrl(objectUrl);
    setPreviewName(file.name);
  }

  async function save() {
    if (!selected) return;
    const saved = previewUrl
      ? await media.saveUploadedImage(
          selected.cardId,
          "/card-images/custom-placeholder.webp",
          transform
        )
      : await media.saveTransform(selected.cardId, transform);
    releasePreview();
    setCards((items) =>
      items.map((item) => item.cardId === selected.cardId ? { ...item, media: saved } : item)
    );
  }

  async function clear() {
    if (!selected) return;
    const saved = await media.clearImage(selected.cardId);
    releasePreview();
    setTransform(structuredClone(saved.transform));
    setCards((items) =>
      items.map((item) => item.cardId === selected.cardId ? { ...item, media: saved } : item)
    );
  }

  const displayedImage = previewUrl ?? selected?.media.imageUrl ?? null;
  const imageAlt = previewUrl
    ? `${previewName} 临时预览`
    : selected
      ? `${selected.headword} 的记忆图片`
      : "";

  return <div className={styles.page}>
    <header className={styles.hero}>
      <div><p className={styles.eyebrow}>CARD MEDIA · CURRENT PLAN</p><h1>卡片图片</h1>
        <p>图片与污渍只绑定当前学习计划中的 cardId。</p></div>
    </header>
    <AsyncState error={error} onRetry={load} status={status}>
      <div className={styles.workspace}>
        <Panel title="当前计划卡片">
          <ul aria-label="选择学习卡" className={styles.cardList}>
            {cards.map((card) => <li key={card.cardId}>
              <button
                aria-pressed={card.cardId === selectedCardId}
                className={styles.cardOption}
                onClick={() => selectCard(card.cardId)}
                type="button"
              >
                <strong>{card.headword}</strong><span>{card.definition}</span><code>{card.cardId}</code>
              </button>
            </li>)}
          </ul>
        </Panel>
        <Panel title={selected ? `编辑 · ${selected.headword}` : "编辑图片"}>
          {selected ? <ImageEditor
            fieldError={fieldError}
            fileInputRef={fileInputRef}
            hasTemporaryPreview={previewUrl !== null}
            imageAlt={imageAlt}
            imageUrl={displayedImage}
            onCancelPreview={releasePreview}
            onClear={() => { void clear(); }}
            onFileChange={chooseFile}
            onSave={() => { void save(); }}
            onTransformChange={setTransform}
            transform={transform}
          /> : null}
        </Panel>
      </div>
    </AsyncState>
  </div>;
}
