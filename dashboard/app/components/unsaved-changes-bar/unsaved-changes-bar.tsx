import { useTranslation } from "react-i18next";
import { MainButton } from "~/components/main-button/main-button";
import classes from "./unsaved-changes-bar.module.css";

interface UnsavedChangesBarProps {
  onCancel: () => void;
  onSave: () => void;
  loading?: boolean;
  disabled?: boolean;
  message?: string;
  cancelLabel?: string;
  saveLabel?: string;
}

export function UnsavedChangesBar({
  onCancel,
  onSave,
  loading = false,
  disabled = false,
  message,
  cancelLabel,
  saveLabel,
}: UnsavedChangesBarProps) {
  const { t } = useTranslation();

  return (
    <div className={classes.bar}>
      <span className={classes.note}>
        <span className={classes.dot} />
        {message ?? (t("bookingDetails.unsavedChanges") as string)}
      </span>
      <MainButton variant="default" onClick={onCancel} disabled={loading}>
        {cancelLabel ?? (t("bookingDetails.cancel") as string)}
      </MainButton>
      <MainButton onClick={onSave} loading={loading} disabled={disabled}>
        {saveLabel ?? (t("bookingDetails.saveChanges") as string)}
      </MainButton>
    </div>
  );
}
