import type { ChangeEvent, RefObject } from "react";
import { Button } from "@/components/Button/Button";
import type { ImageTransform } from "@/domain/media";
import styles from "./media.module.css";

interface ImageEditorProps {
  imageUrl: string | null;
  imageAlt: string;
  transform: ImageTransform;
  fieldError: string | null;
  fileInputRef: RefObject<HTMLInputElement>;
  hasTemporaryPreview: boolean;
  onCancelPreview: () => void;
  onFileChange: (event: ChangeEvent<HTMLInputElement>) => void;
  onTransformChange: (transform: ImageTransform) => void;
  onSave: () => void;
  onClear: () => void;
}

export function ImageEditor({
  imageUrl,
  imageAlt,
  transform,
  fieldError,
  fileInputRef,
  hasTemporaryPreview,
  onCancelPreview,
  onFileChange,
  onTransformChange,
  onSave,
  onClear
}: ImageEditorProps) {
  const rotate = () =>
    onTransformChange({
      ...transform,
      rotation: ((transform.rotation + 90) % 360) as ImageTransform["rotation"]
    });

  return <div className={styles.editor}>
    <div className={styles.previewFrame}>
      {imageUrl ? (
        <img
          alt={imageAlt}
          className={styles.preview}
          src={imageUrl}
          style={{
            transform: `translate(${transform.positionX}px, ${transform.positionY}px) rotate(${transform.rotation}deg) scale(${transform.scale})`
          }}
        />
      ) : (
        <div className={styles.emptyPreview}>这张学习卡还没有记忆图片</div>
      )}
    </div>
    <p className={styles.transformLabel}>旋转 {transform.rotation}°</p>
    <div className={styles.controls}>
      <Button onClick={rotate} variant="secondary">向右旋转</Button>
      <label>缩放
        <input
          aria-label="图片缩放"
          max="1.6"
          min="0.6"
          onChange={(event) => onTransformChange({ ...transform, scale: Number(event.target.value) })}
          step="0.1"
          type="range"
          value={transform.scale}
        />
      </label>
      <label>水平位置
        <input
          aria-label="图片水平位置"
          max="40"
          min="-40"
          onChange={(event) => onTransformChange({ ...transform, positionX: Number(event.target.value) })}
          type="range"
          value={transform.positionX}
        />
      </label>
    </div>
    <label className={styles.fileField}>选择图片
      <input
        accept="image/jpeg,image/png,image/webp"
        aria-describedby={fieldError ? "media-file-error" : undefined}
        onChange={onFileChange}
        ref={fileInputRef}
        type="file"
      />
    </label>
    {fieldError ? <p className={styles.fieldError} id="media-file-error" role="alert">{fieldError}</p> : null}
    <div className={styles.actions}>
      <Button onClick={onSave}>保存图片位置</Button>
      {hasTemporaryPreview ? <Button onClick={onCancelPreview} variant="secondary">取消临时预览</Button> : null}
      <Button onClick={onClear} variant="ghost">清除图片</Button>
    </div>
  </div>;
}
