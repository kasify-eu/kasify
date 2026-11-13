import { useState } from "react";
import { Text } from "@mantine/core";
import { IconCheck, IconCopy } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { showSuccessToast } from "~/lib/toast";
import { getPublicAppUrl } from "~/lib/public-url";
import classes from "~/components/details-drawer/details-drawer.module.css";

interface PublicLinkSectionProps {
  sectionNum: string;
  publicToken: string;
}

export function PublicLinkSection({ sectionNum, publicToken }: PublicLinkSectionProps) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);

  const publicUrl = `${getPublicAppUrl()}/tasks/public?token=${publicToken}`;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(publicUrl);
      setCopied(true);
      showSuccessToast(t("cleaningDetails.copied"));
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // ignore
    }
  };

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("cleaningDetails.publicLink") as string}
        </div>
      </div>
      <Text size="xs" c="dimmed" mb="sm">
        {t("cleaningDetails.publicLinkDescription")}
      </Text>
      <div className={classes.ciLink}>
        <input type="text" readOnly value={publicUrl} className="url" />
        <button
          type="button"
          className={`copyBtn ${copied ? "copied" : ""}`}
          onClick={handleCopy}
          aria-label={t("cleaningDetails.copyLink") as string}
        >
          {copied ? <IconCheck /> : <IconCopy />}
        </button>
      </div>
    </div>
  );
}
