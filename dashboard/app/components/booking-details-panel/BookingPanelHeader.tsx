import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import {
  IconBrandAirbnb,
  IconBrandBooking,
  IconClock,
  IconWorld,
  IconX,
} from "@tabler/icons-react";
import type { Booking } from "~/types/bookings";
import classes from "~/components/details-drawer/details-drawer.module.css";
import { StayTimesEditor } from "./StayTimesEditor";

interface ChannelVisual {
  icon: ReactElement;
  name: string;
}

function getChannelVisual(channel: string | null): ChannelVisual {
  const ch = (channel || "").toLowerCase();
  if (ch.includes("airbnb")) {
    return {
      icon: <IconBrandAirbnb size={22} color="var(--brand-airbnb)" stroke={1.8} />,
      name: "Airbnb",
    };
  }
  if (ch.includes("booking")) {
    return {
      icon: <IconBrandBooking size={22} color="var(--brand-booking)" stroke={1.8} />,
      name: "Booking.com",
    };
  }
  return {
    icon: <IconWorld size={22} color="var(--ink-2)" stroke={1.8} />,
    name: channel || "Direct",
  };
}

interface BookingPanelHeaderProps {
  booking: Booking;
  checkInShort: string;
  checkOutShort: string;
  totalNights: number;
  guestFullName: string;
  canEditTimes: boolean;
  editingTimes: boolean;
  timesLoading: boolean;
  onStartEditingTimes: () => void;
  onSaveTimes: (checkinTime: string | null, checkoutTime: string | null) => void;
  onCancelEditingTimes: () => void;
  onClose: () => void;
}

export function BookingPanelHeader({
  booking,
  checkInShort,
  checkOutShort,
  totalNights,
  guestFullName,
  canEditTimes,
  editingTimes,
  timesLoading,
  onStartEditingTimes,
  onSaveTimes,
  onCancelEditingTimes,
  onClose,
}: BookingPanelHeaderProps) {
  const { t } = useTranslation();
  const channelVisual = getChannelVisual(booking.channel);

  const renderTime = (value: string | null) => {
    if (canEditTimes) {
      const empty = !value;
      return (
        <button
          type="button"
          className={classes.timeChip}
          data-empty={empty}
          onClick={onStartEditingTimes}
          aria-label={t("bookingDetails.editTime") as string}
        >
          <IconClock className={classes.timeChipIcon} stroke={2} />
          <span>
            {value || (t("bookingDetails.stayTimes.setTime") as string)}
          </span>
        </button>
      );
    }
    if (!value) return null;
    return <div className={classes.stayTime}>{value}</div>;
  };

  return (
    <div className={classes.dhead}>
      <div className={classes.dheadTop}>
        <div className={classes.channelBadge}>{channelVisual.icon}</div>
        <div className={classes.dheadTitleWrap}>
          <div className={classes.dheadKicker}>
            <span>{booking.property?.name || ""}</span>
          </div>
          <h2>{guestFullName}</h2>
        </div>
        <div className={classes.dheadActions}>
          <button
            type="button"
            className={`${classes.iconBtn} ${classes.iconBtnClose}`}
            onClick={onClose}
            aria-label={t("bookingDetails.close") as string}
          >
            <IconX />
          </button>
        </div>
      </div>

      {editingTimes ? (
        <StayTimesEditor
          checkinTime={booking.checkin_time}
          checkoutTime={booking.checkout_time}
          checkInShort={checkInShort}
          checkOutShort={checkOutShort}
          totalNights={totalNights}
          loading={timesLoading}
          onSave={onSaveTimes}
          onCancel={onCancelEditingTimes}
        />
      ) : (
        <div className={classes.stayBar}>
          <div className={classes.stayCol}>
            <div className={classes.stayLabel}>
              {t("bookingDetails.checkIn") as string}
            </div>
            <div className={classes.stayVal}>{checkInShort}</div>
            {renderTime(booking.checkin_time)}
          </div>
          <div className={classes.stayMiddle}>
            <div className="count">
              {totalNights} {t("bookingDetails.nights") as string}
            </div>
          </div>
          <div className={`${classes.stayCol} ${classes.stayColEnd}`}>
            <div className={classes.stayLabel}>
              {t("bookingDetails.checkOut") as string}
            </div>
            <div className={classes.stayVal}>{checkOutShort}</div>
            {renderTime(booking.checkout_time)}
          </div>
        </div>
      )}
    </div>
  );
}
