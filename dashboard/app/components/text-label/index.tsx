import type { CSSProperties } from "react";
import classes from "./index.module.css";

interface TextLabelProps {
  label: string;
  text: string;
  mono?: boolean;
  style?: CSSProperties;
}

export function TextLabel({ label, text, mono = false, style }: TextLabelProps) {
  return (
    <div className={classes.wrapper} style={style}>
      <div className={classes.label}>{label}</div>
      <div className={`${classes.value} ${mono ? classes.mono : ""}`}>{text}</div>
    </div>
  );
}
