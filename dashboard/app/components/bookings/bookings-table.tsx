import {
  IconBabyCarriage,
  IconCircleCheckFilled,
  IconCircleXFilled,
  IconClipboard,
  IconClipboardCheck,
  IconClipboardText,
  IconClipboardX,
  IconMoodKid,
  IconShield,
  IconShieldCheck,
  IconShieldHalf,
  IconShieldX,
  IconUser,
  IconUserCheck,
} from "@tabler/icons-react";
import { Tooltip } from "@mantine/core";
import type { Booking } from "~/types/bookings";
import { useTranslation } from "react-i18next";
import { computeSesCommState } from "~/lib/ses-status";
import type { SesCommunicationType } from "~/types/ses-monitoring";
import {
  formatDateRange,
  getChannelIcon,
  getGuestName,
} from "./booking-utils";

/**
 * Joined icon group: single bordered pill, no separation between the icons. With
 * `onClick` the whole pill becomes a clickable chip with a hover state, swallowing the
 * click so it doesn't bubble into the (also clickable) table row.
 */
function IconPill({
  children,
  onClick,
}: {
  children: React.ReactNode;
  onClick?: () => void;
}) {
  return (
    <div
      className={`inline-flex items-center gap-1 px-2 py-1 rounded-lg border border-slate-200 bg-white ${
        onClick
          ? "group/pill cursor-pointer transition-colors hover:bg-slate-900 hover:border-slate-900"
          : ""
      }`}
      onClick={
        onClick
          ? (e) => {
              e.stopPropagation();
              onClick();
            }
          : undefined
      }
    >
      {children}
    </div>
  );
}

function ChannelIcon({ booking }: { booking: Booking }) {
  const channel = getChannelIcon(booking.channel);
  if (!channel) return null;
  return (
    <Tooltip label={channel.label} withArrow>
      <span className="inline-flex items-center">{channel.icon}</span>
    </Tooltip>
  );
}

function CheckinIcon({ booking }: { booking: Booking }) {
  const { t } = useTranslation();
  const checkedIn = !!booking.checkin_completed_at;
  return (
    <Tooltip
      label={checkedIn ? t("bookings.checkinCompleted") : t("bookings.checkinPending")}
      withArrow
    >
      <span className="inline-flex items-center">
        <IconUserCheck size={16} className={checkedIn ? "text-green-600" : "text-slate-200"} />
      </span>
    </Tooltip>
  );
}

/** Confirmed/cancelled indicator (used by the admin table). */
function BookingConfirmationIcon({ booking }: { booking: Booking }) {
  const isCancelled = booking.status.toLowerCase() === "cancelled";
  return (
    <Tooltip label={booking.status} withArrow>
      <span className="inline-flex items-center">
        {isCancelled ? (
          <IconCircleXFilled size={16} className="text-red-500" />
        ) : (
          <IconCircleCheckFilled size={16} className="text-green-500" />
        )}
      </span>
    </Tooltip>
  );
}

// PV (Parte de Viajeros) uses shields; RH (Registro de Hospedaje) uses clipboards so the
// two SES filings are distinguishable at a glance when shown side by side.
const SES_ICON_SPECS: Record<
  SesCommunicationType,
  Record<"sent" | "in_progress" | "error" | "not_sent", { Icon: typeof IconShield; className: string; labelKey: string }>
> = {
  pv: {
    sent: { Icon: IconShieldCheck, className: "text-green-600", labelKey: "bookings.pvSent" },
    in_progress: { Icon: IconShieldHalf, className: "text-amber-500", labelKey: "bookings.pvInProgress" },
    error: { Icon: IconShieldX, className: "text-red-500", labelKey: "bookings.pvError" },
    not_sent: { Icon: IconShield, className: "text-slate-200", labelKey: "bookings.pvNotSent" },
  },
  rh: {
    sent: { Icon: IconClipboardCheck, className: "text-green-600", labelKey: "bookings.rhSent" },
    in_progress: { Icon: IconClipboardText, className: "text-amber-500", labelKey: "bookings.rhInProgress" },
    error: { Icon: IconClipboardX, className: "text-red-500", labelKey: "bookings.rhError" },
    not_sent: { Icon: IconClipboard, className: "text-slate-200", labelKey: "bookings.rhNotSent" },
  },
};

function SesCommIcon({
  booking,
  type,
}: {
  booking: Booking;
  type: SesCommunicationType;
}) {
  const { t } = useTranslation();
  const state = computeSesCommState(booking.ses_communications, type) ?? "not_sent";
  const { Icon, className, labelKey } = SES_ICON_SPECS[type][state];
  return (
    <Tooltip label={t(labelKey)} withArrow>
      <span className="inline-flex items-center">
        {/* group-hover only fires inside a clickable pill (which sets `group/pill`):
            the pill inverts to a dark background, so the icons flip to white. */}
        <Icon
          size={16}
          className={`${className} group-hover/pill:text-white transition-colors`}
        />
      </span>
    </Tooltip>
  );
}

/**
 * Channel · check-in · SES Hospedajes PV state, rendered as one joined icon group. Each
 * icon has its own tooltip. `showBookingStatus` appends a confirmed/cancelled indicator.
 */
export function BookingStatusIcons({
  booking,
  showBookingStatus = false,
}: {
  booking: Booking;
  showBookingStatus?: boolean;
}) {
  return (
    <IconPill>
      <ChannelIcon booking={booking} />
      <CheckinIcon booking={booking} />
      <SesCommIcon booking={booking} type="pv" />
      {showBookingStatus && <BookingConfirmationIcon booking={booking} />}
    </IconPill>
  );
}

/**
 * Channel · check-in · confirmed/cancelled — the admin bookings table's first icon
 * column.
 */
export function BookingChannelStatusIcons({ booking }: { booking: Booking }) {
  return (
    <IconPill>
      <ChannelIcon booking={booking} />
      <CheckinIcon booking={booking} />
      <BookingConfirmationIcon booking={booking} />
    </IconPill>
  );
}

/**
 * PV (parte de viajeros) · RH (registro de hospedajes) — the admin bookings table's SES
 * compliance column. `onSesClick` makes the two SES icons clickable (e.g. to open the SES
 * details drawer) without triggering the row's own click handler.
 */
export function BookingComplianceIcons({
  booking,
  onSesClick,
}: {
  booking: Booking;
  onSesClick?: () => void;
}) {
  return (
    <IconPill onClick={onSesClick}>
      <SesCommIcon booking={booking} type="pv" />
      <SesCommIcon booking={booking} type="rh" />
    </IconPill>
  );
}

function GuestCountIcons({ booking }: { booking: Booking }) {
  const adults = booking.adults_count ?? 0;
  const children = booking.children_count ?? 0;
  const infants = booking.infants_count ?? 0;
  const unknown = adults === 0 && children === 0 && infants === 0;
  return (
    <div className="flex items-center gap-2 mt-0.5">
      {unknown ? (
        <span className="flex items-center gap-0.5 text-slate-400">
          <IconUser size={11} />
          <span className="text-[10px]">-</span>
        </span>
      ) : adults > 0 ? (
        <span className="flex items-center gap-0.5 text-slate-400">
          <IconUser size={11} />
          <span className="text-[10px]">{adults}</span>
        </span>
      ) : null}
      {children > 0 && (
        <span className="flex items-center gap-0.5 text-slate-400">
          <IconMoodKid size={11} />
          <span className="text-[10px]">{children}</span>
        </span>
      )}
      {infants > 0 && (
        <span className="flex items-center gap-0.5 text-slate-400">
          <IconBabyCarriage size={11} />
          <span className="text-[10px]">{infants}</span>
        </span>
      )}
    </div>
  );
}

interface BookingsTableProps {
  bookings: Booking[];
  onBookingClick: (bookingId: string) => void;
  showPropertyName?: boolean;
  showHeader?: boolean;
}

export function BookingsTable({
  bookings,
  onBookingClick,
  showPropertyName = true,
  showHeader = true,
}: BookingsTableProps) {
  const { t } = useTranslation();
  return (
    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          {showHeader && (
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="w-4 pr-0 py-4"></th>
                {showPropertyName && (
                  <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                    Property
                  </th>
                )}
                <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                  Guest
                </th>
                <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                  Dates
                </th>
                <th className="px-2.5 py-4 w-px whitespace-nowrap"></th>
              </tr>
            </thead>
          )}
          <tbody className="divide-y divide-slate-100">
            {bookings.length === 0 && (
              <tr>
                <td colSpan={5} className="px-6 py-12 text-center text-sm text-slate-400">
                  {t("bookings.noResultsFiltered")}
                </td>
              </tr>
            )}
            {bookings.map((booking) => {
              const { dateRange, nights } = formatDateRange(booking);
              const today = new Date().toISOString().split("T")[0];
              const isActive = booking.from <= today && today <= booking.to;
              return (
                <tr
                  key={booking.id}
                  role="button"
                  tabIndex={0}
                  onClick={() => onBookingClick(booking.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      onBookingClick(booking.id);
                    }
                  }}
                  className="group hover:bg-slate-50 transition-colors cursor-pointer"
                >
                  <td className="w-4 pl-3 pr-0 py-4">
                    {isActive && (
                      <span className="inline-block w-2 h-2 rounded-full bg-green-500" />
                    )}
                  </td>
                  {showPropertyName && (
                    <td className="px-2.5 py-4">
                      <div>
                        <p className="font-semibold text-xs text-slate-900">
                          {booking.property.name || "Unknown Property"}
                        </p>
                        <p
                          className="text-xs text-slate-500 max-w-[120px] truncate"
                          title={booking.channel_id ?? undefined}
                        >
                          {booking.channel_id
                            ? `#${booking.channel_id}`
                            : "# -"}
                        </p>
                      </div>
                    </td>
                  )}
                  <td className="px-2.5 py-4">
                    <div>
                      <p className="font-medium text-xs text-slate-900">
                        {getGuestName(booking)}
                      </p>
                      <GuestCountIcons booking={booking} />
                    </div>
                  </td>
                  <td className="px-2.5 py-4">
                    <div className="flex flex-col">
                      <span className="font-medium text-xs text-slate-900">
                        {dateRange}
                      </span>
                      <span className="text-xs text-slate-500">{nights}</span>
                    </div>
                  </td>
                  <td className="px-2 py-4">
                    <BookingStatusIcons booking={booking} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
