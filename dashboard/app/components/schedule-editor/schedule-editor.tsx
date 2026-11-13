import { DateTimePicker } from "@mantine/dates";
import shared from "~/components/details-drawer/details-drawer.module.css";
import local from "./schedule-editor.module.css";

const classes = { ...shared, ...local };

interface ScheduleEditorProps {
  from: string | null;
  to: string | null;
  onFromChange: (value: string | null) => void;
  onToChange: (value: string | null) => void;
  disabled?: boolean;
  startLabel: string;
  endLabel: string;
  errorText?: string;
}

// API returns ISO strings like "2026-05-07T14:00:00.000Z"; Mantine's
// DateTimePicker wants/produces "YYYY-MM-DD HH:mm:ss" without zone info.
// These helpers convert at the input boundary only.
function toPickerValue(iso: string | null): string | null {
  if (!iso) return null;
  return iso.replace("T", " ").replace("Z", "").slice(0, 19);
}

function fromPickerValue(value: string | null): string | null {
  if (!value) return null;
  const trimmed = value.length === 16 ? `${value}:00` : value;
  return `${trimmed.replace(" ", "T")}.000Z`;
}

export function ScheduleEditor({
  from,
  to,
  onFromChange,
  onToChange,
  disabled = false,
  startLabel,
  endLabel,
  errorText,
}: ScheduleEditorProps) {
  return (
    <div className={classes.bar}>
      <div className={classes.stayCol}>
        <div className={classes.stayLabel}>{startLabel}</div>
        <div className={classes.chipWrap}>
          <DateTimePicker
            value={toPickerValue(from)}
            onChange={(v) => onFromChange(fromPickerValue(v))}
            disabled={disabled}
            valueFormat="MMM D, HH:mm"
            clearable={false}
            aria-label={startLabel}
          />
        </div>
      </div>
      <div className={`${classes.stayCol} ${classes.stayColEnd}`}>
        <div className={classes.stayLabel}>{endLabel}</div>
        <div className={classes.chipWrap} data-align="end">
          <DateTimePicker
            value={toPickerValue(to)}
            onChange={(v) => onToChange(fromPickerValue(v))}
            disabled={disabled}
            valueFormat="MMM D, HH:mm"
            clearable={false}
            aria-label={endLabel}
            error={errorText}
          />
        </div>
      </div>
    </div>
  );
}
