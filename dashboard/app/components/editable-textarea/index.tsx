import type { CSSProperties } from "react";
import { TextLabel } from "~/components/text-label";
import classes from "./index.module.css";

interface EditableTextareaProps {
  label: string;
  value: string;
  edit: boolean;
  placeholder?: string;
  onChange?: (event: React.ChangeEvent<HTMLTextAreaElement>) => void;
  disabled?: boolean;
  style?: CSSProperties;
  rows?: number;
}

export function EditableTextarea({
  label,
  value,
  edit,
  placeholder,
  onChange,
  disabled = false,
  style,
  rows,
}: EditableTextareaProps) {
  if (!edit) {
    return <TextLabel label={label} text={value || "-"} style={style} />;
  }

  return (
    <div className={classes.wrapper} style={style}>
      <label className={classes.label}>{label}</label>
      <textarea
        className={classes.textarea}
        value={value}
        disabled={disabled}
        placeholder={placeholder}
        onChange={onChange}
        rows={rows}
      />
    </div>
  );
}
