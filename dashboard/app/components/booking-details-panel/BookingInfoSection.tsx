import type React from "react";
import { useTranslation } from "react-i18next";
import { Grid } from "@mantine/core";
import { TextLabel } from "~/components/text-label";
import shared from "~/components/details-drawer/details-drawer.module.css";
import local from "./booking-details-panel.module.css";
import {
  SesBanner,
  SesClockIcon,
  SesShieldAlertIcon,
  SesShieldCheckIcon,
  type SesBannerVariant,
} from "./SesBanner";
import {
  communicationStatus,
  type SesCommunicationDto,
} from "~/types/ses-monitoring";

const classes = { ...shared, ...local };

interface BookingInfoSectionProps {
  sectionNum: string;
  bookedAt: string;
  channel: string | null;
  channelId: string | null;
  totalNights: number;
  sesCommunications?: SesCommunicationDto[];
}

function formatSesDateTime(iso: string, lang: string): string {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return "";
  const date = d.toLocaleDateString(lang, { month: "short", day: "numeric", year: "numeric" });
  const time = d.toLocaleTimeString(lang, { hour: "2-digit", minute: "2-digit" });
  return `${date} · ${time}`;
}

export function BookingInfoSection({
  sectionNum,
  bookedAt,
  channel,
  channelId,
  totalNights,
  sesCommunications,
}: BookingInfoSectionProps) {
  const { t, i18n } = useTranslation();

  const comms = sesCommunications ?? [];
  const rhStatus = communicationStatus(comms, "rh");

  let rhBanner: React.ReactNode = null;
  if (rhStatus) {
    const alta = comms.find((c) => c.type === "rh" && c.operation === "alta");

    const variant: SesBannerVariant =
      rhStatus === "success"
        ? "ok"
        : rhStatus === "rejected" || rhStatus === "error" || rhStatus === "cancelled"
          ? "fail"
          : "pending";
    const icon =
      variant === "ok" ? SesShieldCheckIcon : variant === "fail" ? SesShieldAlertIcon : SesClockIcon;

    let meta: React.ReactNode;
    if (rhStatus === "success") {
      meta = (
        <>
          {alta?.submitted_at
            ? t("bookingDetails.ses.submittedOn", {
                date: formatSesDateTime(alta.submitted_at, i18n.language),
              })
            : ""}
          {alta?.lote && (
            <>
              {" "}
              · {t("bookingDetails.ses.ref")}{" "}
              <span className={classes.sesRef}>{alta.lote}</span>
            </>
          )}
        </>
      );
    } else if (rhStatus === "rejected" || rhStatus === "error") {
      meta = alta?.last_error ?? alta?.estado_desc ?? (t("bookingDetails.ses.genericError") as string);
    } else if (rhStatus === "cancelled") {
      meta = t("bookingDetails.rh.cancelledMeta") as string;
    } else if (rhStatus === "cancelling") {
      meta = t("bookingDetails.rh.cancellingMeta") as string;
    } else {
      meta = t("bookingDetails.ses.awaitingConfirmation") as string;
    }

    rhBanner = (
      <SesBanner
        variant={variant}
        icon={icon}
        title={t(`bookingDetails.rh.status.${rhStatus}`) as string}
        meta={meta}
      />
    );
  }

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("bookingDetails.bookingSection") as string}
        </div>
      </div>
      <Grid gutter={{ base: 14, sm: 20 }}>
        {bookedAt && (
          <Grid.Col span={6}>
            <TextLabel label={t("bookingDetails.bookedAt") as string} text={bookedAt} />
          </Grid.Col>
        )}
        {channelId && (
          <Grid.Col span={6}>
            <TextLabel
              label={t("bookingDetails.confirmationCode") as string}
              text={channelId}
              mono
            />
          </Grid.Col>
        )}
        {channel && (
          <Grid.Col span={6}>
            <TextLabel label={t("bookingDetails.channel") as string} text={channel} />
          </Grid.Col>
        )}
        <Grid.Col span={6}>
          <TextLabel
            label={t("bookingDetails.totalNights") as string}
            text={String(totalNights)}
          />
        </Grid.Col>
      </Grid>
      {rhBanner && <div style={{ marginTop: 16 }}>{rhBanner}</div>}
    </div>
  );
}
