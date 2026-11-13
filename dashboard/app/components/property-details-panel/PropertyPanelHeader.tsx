import { IconBuildingEstate, IconX } from "@tabler/icons-react";
import type { AdminProperty } from "~/types/admin-lists";
import classes from "~/components/details-drawer/details-drawer.module.css";

interface PropertyPanelHeaderProps {
  property: AdminProperty;
  onClose: () => void;
}

export function PropertyPanelHeader({ property, onClose }: PropertyPanelHeaderProps) {
  const kicker = [property.address, property.city].filter(Boolean).join(", ");

  return (
    <div className={classes.dhead}>
      <div className={classes.dheadTop}>
        <div className={classes.channelBadge}>
          {property.cover_image_url ? (
            <img
              src={property.cover_image_url}
              alt=""
              style={{
                width: "100%",
                height: "100%",
                objectFit: "cover",
                borderRadius: 11,
              }}
            />
          ) : (
            <IconBuildingEstate size={22} stroke={1.8} />
          )}
        </div>
        <div className={classes.dheadTitleWrap}>
          {kicker && (
            <div className={classes.dheadKicker}>
              <span>{kicker}</span>
            </div>
          )}
          <h2>{property.name}</h2>
        </div>
        <div className={classes.dheadActions}>
          <button
            type="button"
            className={`${classes.iconBtn} ${classes.iconBtnClose}`}
            onClick={onClose}
            aria-label="Close"
          >
            <IconX />
          </button>
        </div>
      </div>
    </div>
  );
}
