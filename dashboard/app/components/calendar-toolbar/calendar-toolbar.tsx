import { useMemo, type ReactNode } from "react";
import { ActionIcon } from "@mantine/core";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import classes from "./calendar-toolbar.module.css";

interface CalendarToolbarProps {
  /** First day of the month being displayed. */
  month: Date;
  onPrev: () => void;
  onNext: () => void;
  /** Slot rendered at the left side (e.g. filter chips). */
  left?: ReactNode;
}

export function CalendarToolbar({
  month,
  onPrev,
  onNext,
  left,
}: CalendarToolbarProps) {
  const { t, i18n } = useTranslation();
  const monthFmt = useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language, {
        month: "long",
        year: "numeric",
      }),
    [i18n.language],
  );
  const monthLabel = useMemo(() => {
    const label = monthFmt.format(month);
    return label.charAt(0).toLocaleUpperCase(i18n.language) + label.slice(1);
  }, [monthFmt, month, i18n.language]);

  return (
    <div className={classes.toolbar}>
      <div className={classes.left}>{left}</div>
      <div className={classes.right}>
        <div className={classes.dateNav}>
          <ActionIcon
            variant="subtle"
            color="gray"
            size="md"
            radius="md"
            aria-label={t("calendar.prevMonth")}
            onClick={onPrev}
          >
            <IconChevronLeft size={16} />
          </ActionIcon>
          <div className={classes.monthLabel}>{monthLabel}</div>
          <ActionIcon
            variant="subtle"
            color="gray"
            size="md"
            radius="md"
            aria-label={t("calendar.nextMonth")}
            onClick={onNext}
          >
            <IconChevronRight size={16} />
          </ActionIcon>
        </div>
      </div>
    </div>
  );
}
