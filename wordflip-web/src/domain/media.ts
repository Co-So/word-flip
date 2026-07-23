export interface CardMedia {
  cardId: string;
  imageUrl: string | null;
  stainLevel: 0 | 1 | 2 | 3;
}

export interface MediaRepository {
  getMedia(cardId: string): Promise<CardMedia>;
  saveMedia(media: CardMedia): Promise<CardMedia>;
}
