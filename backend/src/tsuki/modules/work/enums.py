from enum import StrEnum


class MediaType(StrEnum):
    ANIME = "anime"
    MANGA = "manga"
    NOVEL = "novel"
    GAME = "game"


class ReleaseStatus(StrEnum):
    UNKNOWN = "unknown"
    NOT_RELEASED = "not_released"
    RELEASING = "releasing"
    FINISHED = "finished"
    HIATUS = "hiatus"
    CANCELED = "canceled"
