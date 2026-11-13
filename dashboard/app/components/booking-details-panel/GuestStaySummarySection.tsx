import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Grid } from "@mantine/core";
import { EditableTextarea } from "~/components/editable-textarea";
import { UnsavedChangesBar } from "~/components/unsaved-changes-bar/unsaved-changes-bar";
import classes from "~/components/details-drawer/details-drawer.module.css";

export interface StaySummaryData {
  wouldHostAgain: boolean | null;
  reviewNotes: string;
}

interface GuestStaySummarySectionProps {
  sectionNum: string;
  initialData: StaySummaryData;
  /** Job report from the linked cleaning task; null when there is no task or no report yet. */
  checkoutReport: string | null;
  onSave: (data: StaySummaryData) => Promise<void>;
  loading?: boolean;
}

// The review is tri-state on the backend (null = not reviewed) and cannot be
// reset to null once saved, so the "unset" option is only offered while unset.
type WouldHostAgainOption = "" | "yes" | "no";

function toOption(value: boolean | null): WouldHostAgainOption {
  if (value === null) return "";
  return value ? "yes" : "no";
}

export function GuestStaySummarySection({
  sectionNum,
  initialData,
  checkoutReport,
  onSave,
  loading = false,
}: GuestStaySummarySectionProps) {
  const { t } = useTranslation();
  const [wouldHostAgain, setWouldHostAgain] = useState<WouldHostAgainOption>(
    toOption(initialData.wouldHostAgain),
  );
  const [reviewNotes, setReviewNotes] = useState(initialData.reviewNotes);

  useEffect(() => {
    setWouldHostAgain(toOption(initialData.wouldHostAgain));
    setReviewNotes(initialData.reviewNotes);
  }, [initialData]);

  const dirty = useMemo(
    () =>
      wouldHostAgain !== toOption(initialData.wouldHostAgain) ||
      reviewNotes !== initialData.reviewNotes,
    [wouldHostAgain, reviewNotes, initialData],
  );

  const handleSave = async () => {
    await onSave({
      wouldHostAgain: wouldHostAgain === "" ? null : wouldHostAgain === "yes",
      reviewNotes,
    });
  };

  const handleCancel = () => {
    setWouldHostAgain(toOption(initialData.wouldHostAgain));
    setReviewNotes(initialData.reviewNotes);
  };

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("bookingDetails.staySummary") as string}
        </div>
      </div>

      <Grid gutter={{ base: 14, sm: 20 }}>
        <Grid.Col span={6}>
          <div className={classes.field}>
            <label className={classes.fieldLabel}>
              {t("bookingDetails.wouldHostAgain") as string}
            </label>
            <select
              className={classes.fieldInput}
              value={wouldHostAgain}
              onChange={(e) => {
                const value = e.currentTarget.value as WouldHostAgainOption;
                setWouldHostAgain(value);
              }}
            >
              {toOption(initialData.wouldHostAgain) === "" && (
                <option value="">
                  {t("bookingDetails.wouldHostAgainUnset") as string}
                </option>
              )}
              <option value="yes">{t("bookingDetails.wouldHostAgainYes") as string}</option>
              <option value="no">{t("bookingDetails.wouldHostAgainNo") as string}</option>
            </select>
          </div>
        </Grid.Col>
      </Grid>

      <EditableTextarea
        label={t("bookingDetails.guestReviewNotes") as string}
        value={reviewNotes}
        edit
        placeholder={t("bookingDetails.enterGuestReviewNotes") as string}
        onChange={(e) => {
          const value = e.currentTarget.value;
          setReviewNotes(value);
        }}
        style={{ marginTop: 14 }}
      />

      <EditableTextarea
        label={t("bookingDetails.checkoutReport") as string}
        value={checkoutReport ?? ""}
        edit={false}
        style={{ marginTop: 14 }}
      />

      {dirty && (
        <UnsavedChangesBar
          onCancel={handleCancel}
          onSave={handleSave}
          loading={loading}
          disabled={loading}
        />
      )}
    </div>
  );
}
