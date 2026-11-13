import type {TFunction} from "i18next";
import {ApiClientError} from "~/lib/api-client";

export function translateSesHospedajesError(
    err: unknown,
    t: TFunction,
    fallbackKey: string,
): string {
    if (err instanceof ApiClientError) {
        if (err.code === "ses_hospedajes.invalid_credentials") {
            return t("sesHospedajes.error.ses_hospedajes.invalid_credentials");
        }
        if (err.code === "ses_hospedajes.verification_failed") {
            return t("sesHospedajes.error.ses_hospedajes.verification_failed");
        }
        if (err.status === 408) {
            return t("sesHospedajes.error.client.timeout");
        }
    }
    return t(fallbackKey);
}
