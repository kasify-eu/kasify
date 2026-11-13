import { Button, type MantineSize, type MantineRadius } from "@mantine/core";
import type { ReactNode } from "react";
import classes from "./main-button.module.css";

type MainButtonVariant = "primary" | "default" | "ghost" | "danger";

interface MainButtonProps {
  children?: ReactNode;
  component?: "a" | "button";
  href?: string;
  onClick?: () => void;
  fullWidth?: boolean;
  leftSection?: ReactNode;
  rightSection?: ReactNode;
  size?: MantineSize;
  radius?: MantineRadius;
  type?: "button" | "submit" | "reset";
  loading?: boolean;
  disabled?: boolean;
  color?: string;
  variant?: MainButtonVariant;
  ml?: string | number;
  mr?: string | number;
  mt?: string | number;
  mb?: string | number;
  maw?: string | number;
  style?: React.CSSProperties;
  visibleFrom?: string;
  hiddenFrom?: string;
}

const variantClass: Record<MainButtonVariant, string> = {
  primary: classes.variantPrimary,
  default: classes.variantDefault,
  ghost: classes.variantGhost,
  danger: classes.variantDanger,
};

export function MainButton({
  size = "sm",
  radius = "lg",
  variant = "primary",
  children,
  leftSection,
  rightSection,
  component,
  visibleFrom,
  hiddenFrom,
  ...props
}: MainButtonProps) {
  const isLg = size === "lg";
  const hPadding = isLg ? 16 : 14;
  const vPadding = isLg ? 11 : 8;

  return (
    <Button
      component={component}
      {...props}
      size={size}
      radius={radius}
      leftSection={leftSection}
      rightSection={rightSection}
      className={`${classes.mainButton} ${variantClass[variant]} ${isLg ? classes.sizeLg : ""}`}
      visibleFrom={visibleFrom}
      hiddenFrom={hiddenFrom}
      styles={{
        root: {
          paddingLeft: hPadding,
          paddingRight: hPadding,
          paddingTop: vPadding,
          paddingBottom: vPadding,
          height: "auto",
        },
      }}
    >
      {children}
    </Button>
  );
}
