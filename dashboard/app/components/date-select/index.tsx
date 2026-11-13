import { Group, Input, Text } from "@mantine/core";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { KeyboardSelect } from "~/components/keyboard-select";

interface DateSelectProps {
  label?: string;
  value: Date | null;
  onChange: (value: Date | null) => void;
  error?: string;
  maxDate?: Date;
  minYear?: number;
}

function daysInMonth(month: number, year: number): number {
  // month is 1-based
  return new Date(year, month, 0).getDate();
}

function buildDate(
  day: string | null,
  month: string | null,
  year: string | null,
): Date | null {
  if (!day || !month || !year) return null;
  const d = new Date(Number(year), Number(month) - 1, Number(day));
  if (
    d.getFullYear() !== Number(year) ||
    d.getMonth() !== Number(month) - 1 ||
    d.getDate() !== Number(day)
  ) {
    return null;
  }
  return d;
}

export function DateSelect({
  label,
  value,
  onChange,
  error,
  maxDate,
  minYear = 1920,
}: DateSelectProps) {
  const { t } = useTranslation();

  // Internal state tracks partial selections so each select keeps its value
  // even before all three fields are filled.
  const [day, setDay] = useState<string | null>(
    value ? String(value.getDate()) : null,
  );
  const [month, setMonth] = useState<string | null>(
    value ? String(value.getMonth() + 1) : null,
  );
  const [year, setYear] = useState<string | null>(
    value ? String(value.getFullYear()) : null,
  );

  // Sync internal state when the parent resets value to null (e.g. form reset).
  useEffect(() => {
    if (value === null) {
      setDay(null);
      setMonth(null);
      setYear(null);
    }
  }, [value]);

  const maxYear = maxDate ? maxDate.getFullYear() : new Date().getFullYear();

  const yearOptions = useMemo(() => {
    const options = [];
    for (let y = maxYear; y >= minYear; y--) {
      options.push({ value: String(y), label: String(y) });
    }
    return options;
  }, [maxYear, minYear]);

  const monthOptions = useMemo(() => {
    const months: string[] = t("dateSelect.months", {
      returnObjects: true,
    }) as string[];
    return months.map((name, i) => ({
      value: String(i + 1),
      label: name,
    }));
  }, [t]);

  const dayOptions = useMemo(() => {
    const y = year ? Number(year) : maxYear;
    const m = month ? Number(month) : 12;
    const maxDay = daysInMonth(m, y);
    const options = [];
    for (let d = 1; d <= maxDay; d++) {
      options.push({ value: String(d), label: String(d).padStart(2, "0") });
    }
    return options;
  }, [year, month, maxYear]);

  const emitDate = (
    nextDay: string | null,
    nextMonth: string | null,
    nextYear: string | null,
  ) => {
    const date = buildDate(nextDay, nextMonth, nextYear);
    // Only call onChange when we have a complete valid date, or when
    // clearing (parent already had a date and a field was deselected).
    if (date || value) {
      onChange(date);
    }
  };

  const handleDayChange = (v: string | null) => {
    setDay(v);
    emitDate(v, month, year);
  };

  const handleMonthChange = (v: string | null) => {
    // Clamp day if it exceeds the new month's days
    let clampedDay = day;
    if (day && v && year) {
      const maxDay = daysInMonth(Number(v), Number(year));
      if (Number(day) > maxDay) clampedDay = String(maxDay);
    }
    setMonth(v);
    if (clampedDay !== day) setDay(clampedDay);
    emitDate(clampedDay, v, year);
  };

  const handleYearChange = (v: string | null) => {
    // Clamp day if it exceeds the month's days in the new year (Feb 29 → 28)
    let clampedDay = day;
    if (day && month && v) {
      const maxDay = daysInMonth(Number(month), Number(v));
      if (Number(day) > maxDay) clampedDay = String(maxDay);
    }
    setYear(v);
    if (clampedDay !== day) setDay(clampedDay);
    emitDate(clampedDay, month, v);
  };

  return (
    <div>
      {label && (
        <Input.Label
          style={{ fontSize: "0.75rem", fontWeight: 500, color: "#727272", margin: 0, paddingBottom: 0 }}
        >
          {label}
        </Input.Label>
      )}
      <Group gap="xs" grow>
        <KeyboardSelect
          placeholder={t("dateSelect.day")}
          data={dayOptions}
          value={day}
          onChange={handleDayChange}
          error={error ? " " : undefined}
          aria-label={t("dateSelect.day")}
        />
        <KeyboardSelect
          placeholder={t("dateSelect.month")}
          data={monthOptions}
          value={month}
          onChange={handleMonthChange}
          error={error ? " " : undefined}
          aria-label={t("dateSelect.month")}
        />
        <KeyboardSelect
          placeholder={t("dateSelect.year")}
          data={yearOptions}
          value={year}
          onChange={handleYearChange}
          error={error ? " " : undefined}
          aria-label={t("dateSelect.year")}
        />
      </Group>
      {error && error !== " " && (
        <Text size="xs" c="red" mt={4}>
          {error}
        </Text>
      )}
    </div>
  );
}
