// Mirrors eu.kasify.model.api.responses.TelegramLinkResponse / TelegramStatusResponse.

export type TelegramLinkResponse = {
    deep_link: string;
    expires_at: string;
};

export type TelegramLinkStatus = "none" | "pending" | "linked";

export type TelegramStatusResponse = {
    status: TelegramLinkStatus;
    telegram_username?: string | null;
    linked_at?: string | null;
};
