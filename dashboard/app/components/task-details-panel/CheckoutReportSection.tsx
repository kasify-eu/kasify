import { useState } from "react";
import { useTranslation } from "react-i18next";
import { EditableTextarea } from "~/components/editable-textarea";
import { UnsavedChangesBar } from "~/components/unsaved-changes-bar/unsaved-changes-bar";
import classes from "~/components/details-drawer/details-drawer.module.css";

interface CheckoutReportSectionProps {
  sectionNum: string;
  notes: string;
  originalNotes: string;
  onChange: (value: string) => void;
  onSave: () => Promise<void>;
  disabled?: boolean;
  taskType?: string;
}

export function CheckoutReportSection({
  sectionNum,
  notes,
  originalNotes,
  onChange,
  onSave,
  disabled,
  taskType,
}: CheckoutReportSectionProps) {
  const { t } = useTranslation();
  const [saving, setSaving] = useState(false);

  const dirty = notes !== originalNotes;

  const handleSave = async () => {
    setSaving(true);
    try {
      await onSave();
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    onChange(originalNotes);
  };

  const placeholder =
    taskType === "maintenance"
      ? (t("cleaningDetails.checkoutReportPlaceholderMaintenance") as string)
      : (t("cleaningDetails.checkoutReportPlaceholder") as string);

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("cleaningDetails.checkoutReport") as string}
        </div>
      </div>
      <EditableTextarea
        label={t("cleaningDetails.notesOnCondition") as string}
        value={notes}
        edit
        placeholder={placeholder}
        onChange={(e) => onChange(e.currentTarget.value)}
        disabled={disabled}
        rows={6}
      />
      {!disabled && dirty && (
        <UnsavedChangesBar
          onCancel={handleCancel}
          onSave={handleSave}
          loading={saving}
        />
      )}
    </div>
  );
}
