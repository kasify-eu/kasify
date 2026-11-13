import type { ReactNode } from "react";
import { Drawer, LoadingOverlay } from "@mantine/core";
import classes from "./details-drawer.module.css";

interface DetailsDrawerProps {
  opened: boolean;
  onClose: () => void;
  loading?: boolean;
  header?: ReactNode;
  children: ReactNode;
  size?: number | string;
}

export function DetailsDrawer({
  opened,
  onClose,
  loading = false,
  header,
  children,
  size = 560,
}: DetailsDrawerProps) {
  return (
    <Drawer
      opened={opened}
      onClose={onClose}
      position="right"
      size={size}
      withCloseButton={false}
      padding={0}
      styles={{
        body: { padding: 0, height: "100%", display: "flex", flexDirection: "column" },
        content: { display: "flex", flexDirection: "column" },
      }}
    >
      <div className={classes.panel}>
        <LoadingOverlay visible={loading} zIndex={10} />
        {header}
        <div className={classes.dbody}>{children}</div>
      </div>
    </Drawer>
  );
}
