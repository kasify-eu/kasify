import { Button } from "@mantine/core";
import classes from "./secondary-button.module.css";

export function SecondaryButton(props: any) {
  return (
    <Button
      {...props}
      variant="outline"
      className={classes.mainButton}
      style={{
        ...props.style,
      }}
    />
  );
}
