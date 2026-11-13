import { IconMinus, IconPlus } from "@tabler/icons-react";
import classes from "./number-stepper.module.css";

interface NumberStepperProps {
  label: string;
  value: number;
  onChange: (value: number) => void;
  disabled?: boolean;
  min?: number;
  max?: number;
}

export function NumberStepper({
  label,
  value,
  onChange,
  disabled = false,
  min = 0,
  max,
}: NumberStepperProps) {
  const clamp = (n: number) => {
    const lower = Math.max(min, n);
    return max !== undefined ? Math.min(max, lower) : lower;
  };

  return (
    <div className={classes.wrapper}>
      <label className={classes.label}>{label}</label>
      <div className={classes.stepper}>
        <button
          type="button"
          aria-label={`Decrease ${label}`}
          disabled={disabled || value <= min}
          onClick={() => onChange(clamp(value - 1))}
        >
          <IconMinus />
        </button>
        <input
          type="number"
          min={min}
          max={max}
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(clamp(parseInt(e.currentTarget.value || "0", 10)))}
        />
        <button
          type="button"
          aria-label={`Increase ${label}`}
          disabled={disabled || (max !== undefined && value >= max)}
          onClick={() => onChange(clamp(value + 1))}
        >
          <IconPlus />
        </button>
      </div>
    </div>
  );
}
