import { useTranslation } from "react-i18next";
import { Modal } from "@mantine/core";
import { IconTrash, IconX } from "@tabler/icons-react";
import { MainButton } from "~/components/main-button/main-button";
import shared from "~/components/details-drawer/details-drawer.module.css";
import local from "./booking-details-panel.module.css";

const classes = { ...shared, ...local };

interface DeleteAccessCodeModalProps {
  opened: boolean;
  onClose: () => void;
  onConfirm: () => void;
  loading?: boolean;
}

export function DeleteAccessCodeModal({
  opened,
  onClose,
  onConfirm,
  loading = false,
}: DeleteAccessCodeModalProps) {
  const { t } = useTranslation();

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      withCloseButton={false}
      centered
      size={400}
      padding={0}
      radius={20}
      styles={{ content: { borderRadius: 20, overflow: "hidden" } }}
    >
      <div className={`${classes.panel} ${classes.modalCard}`}>
        <button
          type="button"
          className="x"
          onClick={onClose}
          aria-label={t("bookingDetails.close") as string}
        >
          <IconX />
        </button>
        <div className={classes.modalIcon}><IconTrash /></div>
        <h3>{t("bookingDetails.deleteModal.title") as string}</h3>
        <p>{t("bookingDetails.deleteModal.confirm") as string}</p>
        <div className={classes.modalActions}>
          <MainButton variant="ghost" onClick={onClose}>
            {t("bookingDetails.cancel") as string}
          </MainButton>
          <MainButton variant="danger" onClick={onConfirm} loading={loading}>
            {t("bookingDetails.delete") as string}
          </MainButton>
        </div>
      </div>
    </Modal>
  );
}
