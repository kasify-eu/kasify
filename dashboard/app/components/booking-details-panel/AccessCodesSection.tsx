import { useTranslation } from "react-i18next";
import { IconCopy, IconDoor, IconKey, IconTrash } from "@tabler/icons-react";
import { MainButton } from "~/components/main-button/main-button";
import { showSuccessToast } from "~/lib/toast";
import type { AccessCode } from "~/types/bookings";
import shared from "~/components/details-drawer/details-drawer.module.css";
import local from "./booking-details-panel.module.css";

const classes = { ...shared, ...local };

interface AccessCodesSectionProps {
  sectionNum: string;
  accessCodes: AccessCode[];
  onCreateAccessCode: () => void;
  onDeleteAccessCode: (codeId: string) => void;
  loading?: boolean;
  isCompleted?: boolean;
}

export function AccessCodesSection({
  sectionNum,
  accessCodes,
  onCreateAccessCode,
  onDeleteAccessCode,
  loading = false,
  isCompleted = false,
}: AccessCodesSectionProps) {
  const { t } = useTranslation();
  const hasCodes = accessCodes && accessCodes.length > 0;

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("bookingDetails.accessCodes") as string}
        </div>
        {hasCodes && (
          <span className={classes.pill}>
            {t("bookingDetails.codesActive", { count: accessCodes.length }) as string}
          </span>
        )}
      </div>

      {hasCodes ? (
        accessCodes.map((code) => (
          <div key={code.id} className={classes.codeCard}>
            <div className={classes.codeIcon}><IconDoor /></div>
            <div className={classes.codeMain}>
              <div className={classes.codeDigits}>{code.access_code}</div>
              <div className={classes.codeMeta}>{code.smartlock_name}</div>
            </div>
            <div className={classes.codeActions}>
              <button
                type="button"
                className={classes.iconBtn}
                aria-label={t("bookingDetails.copyToClipboard") as string}
                onClick={async () => {
                  await navigator.clipboard.writeText(code.access_code);
                  showSuccessToast(t("bookingDetails.toast.accessCodeCopied"), "📋");
                }}
              >
                <IconCopy />
              </button>
              <button
                type="button"
                className={`${classes.iconBtn} ${classes.iconBtnDangerGhost}`}
                aria-label={t("bookingDetails.deleteAccessCodeTooltip") as string}
                onClick={() => onDeleteAccessCode(code.id)}
              >
                <IconTrash />
              </button>
            </div>
          </div>
        ))
      ) : (
        <div className={classes.emptyAccess}>
          <p>{t("bookingDetails.noAccessCodes") as string}</p>
          {!isCompleted && (
            <MainButton
              onClick={onCreateAccessCode}
              loading={loading}
              leftSection={<IconKey height={18} />}
            >
              {t("bookingDetails.createAccessCodes") as string}
            </MainButton>
          )}
        </div>
      )}
    </div>
  );
}
