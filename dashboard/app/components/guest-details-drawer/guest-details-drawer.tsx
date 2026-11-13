import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Box, Button, Drawer, Menu, Modal, Stack, Text } from "@mantine/core";
import { IconDotsVertical, IconPencil, IconTrash } from "@tabler/icons-react";
import type { Guest } from "~/types/bookings";
import type { GuestRegistrationRequest } from "~/types/checkin";
import classes from "./guest-details-drawer.module.css";
import { GuestDetailsHeader } from "./GuestDetailsHeader";
import { GuestEditForm } from "./GuestEditForm";
import {
  ContactSection,
  DocumentSection,
  PersonalSection,
  ResidenceSection,
} from "./GuestSections";

export type GuestDetailsDrawerMode = "view" | "edit" | "create";

export interface GuestDetailsDrawerProps {
  /**
   * The guest to display. In `view`/`edit` mode this is the row being shown;
   * in `create` mode this should be null — the form starts empty so the owner
   * can introduce data for a pending guest slot.
   */
  guest: Guest | null;
  opened: boolean;
  mode?: GuestDetailsDrawerMode;
  onClose: () => void;
  /**
   * Short booking code (e.g. "HM3MHEW33J") shown as a pill. The booking object
   * itself isn't required.
   */
  bookingCode?: string | null;
  /**
   * SES Hospedajes submission timestamp at the booking level — when set, a
   * "SES synced" pill is shown.
   */
  sesSubmittedAt?: string | null;
  /** Owner/Manager can edit; when true an Edit item appears in the options menu. */
  canEdit?: boolean;
  /** Owner/Manager can delete; when true a Delete item appears in the options menu. */
  canDelete?: boolean;
  /**
   * Called when the user confirms deletion. The parent owns the API call and is
   * responsible for closing the drawer / refetching on success. Reject the promise
   * to keep the confirmation modal open (e.g. on error).
   */
  onDelete?: () => void | Promise<void>;
  /**
   * Called when the form is submitted. The parent owns the API call and is
   * responsible for closing the drawer / refetching the booking on success.
   */
  onSave?: (data: GuestRegistrationRequest) => void | Promise<void>;
  /** Shown as an inline alert above the form. */
  submitError?: string | null;
  saving?: boolean;
  /**
   * Optional slot number shown in `create` mode header (e.g. "Guest 2 of 3").
   */
  slotIndex?: number | null;
  totalSlots?: number | null;
  /**
   * True when the booking the guest belongs to includes any children/infants.
   * Surfaces the SES "parentesco" Select for adult guests in the edit form.
   */
  bookingHasMinors?: boolean;
}

/**
 * Right-side drawer for guest details. Three modes:
 *   - `view`     — read-only sections; owner sees an Edit button in the header
 *   - `edit`     — form pre-filled with the guest's current values
 *   - `create`   — empty form, used to register a pending guest manually
 */
export function GuestDetailsDrawer({
  guest,
  opened,
  mode = "view",
  onClose,
  bookingCode,
  sesSubmittedAt,
  canEdit = false,
  canDelete = false,
  onDelete,
  onSave,
  submitError,
  saving = false,
  slotIndex,
  totalSlots,
  bookingHasMinors = false,
}: GuestDetailsDrawerProps) {
  const { t } = useTranslation();

  // Local mode so the user can flip view → edit without remounting the drawer.
  // Synced from the prop when the drawer (re-)opens.
  const [localMode, setLocalMode] = useState<GuestDetailsDrawerMode>(mode);
  const [deleteConfirmOpened, setDeleteConfirmOpened] = useState(false);
  const [deleting, setDeleting] = useState(false);
  useEffect(() => {
    if (opened) {
      setLocalMode(mode);
    } else {
      setDeleteConfirmOpened(false);
    }
  }, [opened, mode]);

  // Once a booking is submitted to SES Hospedajes the guest data must be retained
  // (Spanish law: 3-year retention), so edit and delete are locked.
  const sesLocked = sesSubmittedAt != null;

  const handleConfirmDelete = async () => {
    if (!onDelete) return;
    setDeleting(true);
    try {
      await onDelete();
      setDeleteConfirmOpened(false);
    } finally {
      setDeleting(false);
    }
  };

  const isRegistered = !!guest;
  const isForm = localMode === "edit" || localMode === "create";

  const createSubtitle =
    localMode === "create" && slotIndex != null
      ? t("guestDetailsDrawer.createTitle", {
          n: slotIndex,
          total: totalSlots ?? slotIndex,
        })
      : null;

  return (
    <>
    <Drawer
      opened={opened}
      onClose={onClose}
      position="right"
      size={540}
      withCloseButton={false}
      padding={0}
      // 250 layers above the booking drawer (Mantine modal default = 200) while
      // staying below the popover/combobox layer (300) — otherwise Mantine Select
      // dropdowns render behind the drawer and appear empty.
      zIndex={250}
      overlayProps={{ backgroundOpacity: 0.18 }}
      classNames={{ root: "guest-details-drawer-overlay" }}
      styles={{
        body: { padding: 0, height: "100%", display: "flex", flexDirection: "column" },
        content: { display: "flex", flexDirection: "column" },
      }}
    >
      <div className={classes.panel}>
        {localMode === "create" ? (
          <CreateHeader
            title={createSubtitle ?? (t("guestDetailsDrawer.createDefaultTitle") as string)}
            bookingCode={bookingCode}
            onClose={onClose}
          />
        ) : guest ? (
          <GuestDetailsHeader
            guest={guest}
            isRegistered={isRegistered}
            sesSubmittedAt={sesSubmittedAt}
            bookingCode={bookingCode}
            onClose={onClose}
            rightActions={
              (canEdit || canDelete) && localMode === "view" ? (
                <Menu position="bottom-end" withinPortal zIndex={400}>
                  <Menu.Target>
                    <button
                      type="button"
                      className={classes.iconBtn}
                      aria-label={t("guestDetailsDrawer.options") as string}
                    >
                      <IconDotsVertical stroke={2} />
                    </button>
                  </Menu.Target>
                  <Menu.Dropdown>
                    {sesLocked && (
                      <Menu.Label>
                        {t("guestDetailsDrawer.sesLockedHint") as string}
                      </Menu.Label>
                    )}
                    {canEdit && (
                      <Menu.Item
                        leftSection={<IconPencil size={16} />}
                        disabled={sesLocked}
                        onClick={() => setLocalMode("edit")}
                      >
                        {t("guestDetailsDrawer.edit") as string}
                      </Menu.Item>
                    )}
                    {canDelete && (
                      <Menu.Item
                        color="red"
                        leftSection={<IconTrash size={16} />}
                        disabled={sesLocked}
                        onClick={() => setDeleteConfirmOpened(true)}
                      >
                        {t("guestDetailsDrawer.delete") as string}
                      </Menu.Item>
                    )}
                  </Menu.Dropdown>
                </Menu>
              ) : null
            }
          />
        ) : null}

        {isForm ? (
          <GuestEditForm
            guest={localMode === "edit" ? guest : null}
            loading={saving}
            submitError={submitError}
            bookingHasMinors={bookingHasMinors}
            onSubmit={(data) => onSave?.(data)}
            onCancel={() => {
              if (localMode === "edit") {
                setLocalMode("view");
              } else {
                onClose();
              }
            }}
          />
        ) : guest ? (
          <div className={classes.body}>
            <PersonalSection guest={guest} sectionNum="01" />
            <DocumentSection guest={guest} sectionNum="02" />
            <ResidenceSection guest={guest} sectionNum="03" />
            <ContactSection guest={guest} sectionNum="04" />
          </div>
        ) : (
          <div className={classes.empty}>
            {t("guestDetailsDrawer.empty") as string}
          </div>
        )}
      </div>
    </Drawer>

    <Modal
      opened={deleteConfirmOpened}
      onClose={() => setDeleteConfirmOpened(false)}
      withCloseButton={false}
      centered
      size="md"
      radius={32}
      padding={0}
      zIndex={400}
      styles={{
        content: {
          borderRadius: 32,
          overflow: "hidden",
        },
      }}
    >
      <Box className="p-8">
        <div className="flex justify-between items-start mb-6">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center">
              <IconTrash size={24} className="text-red-600" />
            </div>
            <Text size="xl" fw={700} className="tracking-tight text-slate-900">
              {t("guestDetailsDrawer.deleteConfirmTitle") as string}
            </Text>
          </div>
          <button
            onClick={() => setDeleteConfirmOpened(false)}
            className="p-2 hover:bg-slate-100 rounded-full transition-colors"
          >
            <svg className="w-5 h-5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <Stack gap="md">
          <Text size="sm" c="dimmed">
            {t("guestDetailsDrawer.deleteConfirmBody") as string}
          </Text>

          <div className="flex gap-3 mt-4">
            <Button
              variant="light"
              color="gray"
              fullWidth
              radius={16}
              onClick={() => setDeleteConfirmOpened(false)}
              styles={{ root: { height: 48, fontWeight: 600 } }}
            >
              {t("common.cancel") as string}
            </Button>
            <Button
              color="red"
              fullWidth
              radius={16}
              loading={deleting}
              onClick={handleConfirmDelete}
              styles={{ root: { height: 48, fontWeight: 600 } }}
            >
              {t("guestDetailsDrawer.delete") as string}
            </Button>
          </div>
        </Stack>
      </Box>
    </Modal>
    </>
  );
}

function CreateHeader({
  title,
  bookingCode,
  onClose,
}: {
  title: string;
  bookingCode?: string | null;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div className={classes.head}>
      <div className={classes.identity}>
        <div className={classes.av} data-tint="b">
          +
        </div>
        <div className={classes.identityText}>
          <h2>{title}</h2>
          {bookingCode && (
            <div className={classes.pillRow}>
              <span className={classes.pill}>
                {t("guestDetailsDrawer.bookingCode", { code: bookingCode }) as string}
              </span>
            </div>
          )}
        </div>
        <div className={classes.headActions}>
          <button
            type="button"
            className={`${classes.iconBtn} ${classes.iconBtnClose}`}
            onClick={onClose}
            aria-label={t("bookingDetails.close") as string}
          >
            ✕
          </button>
        </div>
      </div>
    </div>
  );
}
