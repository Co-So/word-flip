export interface ImageTransform {
  rotation: 0 | 90 | 180 | 270;
  scale: number;
  positionX: number;
  positionY: number;
}

export interface CardMedia {
  cardId: string;
  imageUrl: string | null;
  stainLevel: 0 | 1 | 2 | 3;
  transform: ImageTransform;
}

export interface MediaCard {
  cardId: string;
  headword: string;
  definition: string;
  media: CardMedia;
}

export interface MediaRepository {
  listCards(): Promise<MediaCard[]>;
  getMedia(cardId: string): Promise<CardMedia>;
  saveTransform(cardId: string, transform: ImageTransform): Promise<CardMedia>;
  saveUploadedImage(cardId: string, fixedAssetPath: string, transform: ImageTransform): Promise<CardMedia>;
  clearImage(cardId: string): Promise<CardMedia>;
}
