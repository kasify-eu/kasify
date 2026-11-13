import { useTranslation } from "react-i18next";
import {
  IconBucketDroplet,
  IconCalendarTime,
  IconDotsVertical,
  IconPlant,
  IconSparkles,
  IconTools,
  IconTrash,
  IconX,
} from "@tabler/icons-react";
import { Menu } from "@mantine/core";
import { MainButton } from "~/components/main-button/main-button";
import { ScheduleEditor } from "~/components/schedule-editor/schedule-editor";
import type { Task, TaskType } from "~/types/tasks";
import classes from "~/components/details-drawer/details-drawer.module.css";

interface TaskPanelHeaderProps {
  task: Task;
  from: string | null;
  to: string | null;
  onFromChange: (value: string | null) => void;
  onToChange: (value: string | null) => void;
  scheduleDirty: boolean;
  scheduleValid: boolean;
  scheduleSaving: boolean;
  onSaveSchedule: () => void;
  onCancelSchedule: () => void;
  canEdit: boolean;
  onClose: () => void;
  onDelete: () => void;
}

export function TaskPanelHeader({
  task,
  from,
  to,
  onFromChange,
  onToChange,
  scheduleDirty,
  scheduleValid,
  scheduleSaving,
  onSaveSchedule,
  onCancelSchedule,
  canEdit,
  onClose,
  onDelete,
}: TaskPanelHeaderProps) {
  const { t } = useTranslation();

  const TYPE_LABELS: Record<TaskType, string> = {
    cleaning: t("cleaningDetails.taskTypeCleaning") as string,
    maintenance: t("cleaningDetails.taskTypeMaintenance") as string,
    pool: t("tasks.type.pool") as string,
    gardening: t("tasks.type.gardening") as string,
  };
  // task_type is absent when the backend redacted a foreign-task response.
  const typeLabel = task.task_type
    ? TYPE_LABELS[task.task_type]
    : (t("taskDetails.foreignTask.kicker") as string);
  const TypeIcon = (() => {
    switch (task.task_type) {
      case "cleaning":
        return IconSparkles;
      case "maintenance":
        return IconTools;
      case "pool":
        return IconBucketDroplet;
      case "gardening":
        return IconPlant;
      default:
        return IconCalendarTime;
    }
  })();

  const isCompleted = task.status === "completed";
  const scheduleErrorText =
    scheduleDirty && !scheduleValid
      ? (t("bookingDetails.error.task.invalid_schedule") as string)
      : undefined;

  return (
    <div className={classes.dhead}>
      <div className={classes.dheadTop}>
        <div className={classes.channelBadge}>
          <TypeIcon stroke={1.8} />
        </div>
        <div className={classes.dheadTitleWrap}>
          <div className={classes.dheadKicker}>
            <span>{typeLabel}</span>
          </div>
          <h2>{task.property?.name || ""}</h2>
        </div>
        <div className={classes.dheadActions}>
          {canEdit && (
            <Menu position="bottom-end" withinPortal>
              <Menu.Target>
                <button
                  type="button"
                  className={classes.iconBtn}
                  aria-label={t("cleaningDetails.taskOptions") as string}
                >
                  <IconDotsVertical />
                </button>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Item
                  color="red"
                  leftSection={<IconTrash size={16} />}
                  onClick={onDelete}
                >
                  {t("cleaningDetails.deleteTask") as string}
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          )}
          <button
            type="button"
            className={`${classes.iconBtn} ${classes.iconBtnClose}`}
            onClick={onClose}
            aria-label={t("common.close") as string}
          >
            <IconX />
          </button>
        </div>
      </div>

      <ScheduleEditor
        from={from}
        to={to}
        onFromChange={onFromChange}
        onToChange={onToChange}
        disabled={!canEdit || isCompleted}
        startLabel={t("cleaningDetails.scheduleStart") as string}
        endLabel={t("cleaningDetails.scheduleEnd") as string}
        errorText={scheduleErrorText}
      />

      {scheduleDirty && (
        <div className={classes.stayTimesFoot}>
          <MainButton
            variant="ghost"
            onClick={onCancelSchedule}
            disabled={scheduleSaving}
          >
            {t("bookingDetails.cancel") as string}
          </MainButton>
          <MainButton
            variant="primary"
            onClick={onSaveSchedule}
            loading={scheduleSaving}
            disabled={!scheduleValid}
          >
            {t("bookingDetails.save") as string}
          </MainButton>
        </div>
      )}
    </div>
  );
}
