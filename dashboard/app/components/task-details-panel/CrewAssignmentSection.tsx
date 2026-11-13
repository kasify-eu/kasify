import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Text } from "@mantine/core";
import { CleanerSelect } from "~/components/cleaner-select";
import { EditableTextarea } from "~/components/editable-textarea";
import { UnsavedChangesBar } from "~/components/unsaved-changes-bar/unsaved-changes-bar";
import type { PropertyMember } from "~/types/properties";
import classes from "~/components/details-drawer/details-drawer.module.css";

interface CrewAssignmentSectionProps {
  sectionNum: string;
  team: PropertyMember[];
  assignedTo: string | null;
  originalAssignedTo: string | null;
  notes: string;
  originalNotes: string;
  onAssignedToChange: (value: string | null) => void;
  onNotesChange: (value: string) => void;
  onSave: () => Promise<void>;
  canEdit: boolean;
  disabled?: boolean;
}

export function CrewAssignmentSection({
  sectionNum,
  team,
  assignedTo,
  originalAssignedTo,
  notes,
  originalNotes,
  onAssignedToChange,
  onNotesChange,
  onSave,
  canEdit,
  disabled,
}: CrewAssignmentSectionProps) {
  const { t } = useTranslation();
  const [saving, setSaving] = useState(false);

  const dirty =
    assignedTo !== originalAssignedTo || notes !== originalNotes;

  const handleSave = async () => {
    setSaving(true);
    try {
      await onSave();
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    onAssignedToChange(originalAssignedTo);
    onNotesChange(originalNotes);
  };

  if (!canEdit) {
    return (
      <div className={classes.section}>
        <div className={classes.sectionHead}>
          <div className={classes.sectionLabel}>
            <span className={classes.num}>{sectionNum}</span>
            {t("cleaningDetails.crewSection") as string}
          </div>
          <span className={classes.pill}>
            <span className="dotp" />
            {t("bookingDetails.readOnly") as string}
          </span>
        </div>
        <Text size="xs" c="dimmed" mb={4}>
          {t("cleaningDetails.notesForCrew") as string}
        </Text>
        <Text size="sm" c={notes ? "dark" : "dimmed"}>
          {notes || t("cleaningDetails.notesPlaceholder")}
        </Text>
      </div>
    );
  }

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("cleaningDetails.crewSection") as string}
        </div>
      </div>

      <CleanerSelect
        team={team}
        value={assignedTo}
        label={t("cleaningDetails.assignedMember") as string}
        onChange={onAssignedToChange}
        disabled={disabled}
      />

      <EditableTextarea
        label={t("cleaningDetails.notesForCrew") as string}
        value={notes}
        edit
        placeholder={t("cleaningDetails.notesPlaceholder") as string}
        onChange={(e) => onNotesChange(e.currentTarget.value)}
        disabled={disabled}
        rows={6}
        style={{ marginTop: 14 }}
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
