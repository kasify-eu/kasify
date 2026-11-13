import type { Guest } from "~/types/bookings";
import type { Property } from "~/types/properties";
import { useTranslation } from "react-i18next";
import {
  IconMail,
  IconMailCheck,
  IconMailOff,
  IconThumbDown,
  IconThumbUp,
  IconWorld,
} from "@tabler/icons-react";
import { Tooltip } from "@mantine/core";
import { getChannelIcon } from "~/components/bookings/booking-utils";

interface GuestsTableProps {
  guests: Guest[];
  properties: Property[];
  onGuestClick?: (guest: Guest) => void;
  onBookingClick?: (bookingId: string) => void;
}

function countryFlag(alpha2?: string | null): string | null {
  if (!alpha2 || alpha2.length !== 2) return null;
  const code = alpha2.toUpperCase();
  if (!/^[A-Z]{2}$/.test(code)) return null;
  const base = 0x1f1e6;
  return String.fromCodePoint(
    base + code.charCodeAt(0) - 65,
    base + code.charCodeAt(1) - 65,
  );
}

/**
 * Channel · would-host-again · email-consent · nationality flag, rendered as one joined icon
 * group (single bordered pill), mirroring `BookingStatusIcons` on the /bookings table. Each
 * icon has its own tooltip. `would_host_again` is owner-private and arrives null for managers,
 * which renders as the faded "review pending" state. Email consent has three states: opted in
 * (green), unsubscribed after consenting (red — emailing them violates LSSI Art. 21), and
 * never opted in (faded).
 */
function GuestStatusIcons({ guest }: { guest: Guest }) {
  const { t, i18n } = useTranslation();
  const channel = getChannelIcon(guest.channel);
  const flag = countryFlag(guest.nationality?.alpha_2);
  const nationalityName = i18n.language.startsWith("es")
    ? guest.nationality?.name_es
    : guest.nationality?.name;

  const hostAgain = (() => {
    if (guest.would_host_again === true) {
      return { Icon: IconThumbUp, className: "text-green-600", label: t("guests.hostAgainYes") };
    }
    if (guest.would_host_again === false) {
      return { Icon: IconThumbDown, className: "text-red-500", label: t("guests.hostAgainNo") };
    }
    return { Icon: IconThumbUp, className: "text-slate-200", label: t("guests.hostAgainUnset") };
  })();
  const HostIcon = hostAgain.Icon;

  const emailConsent = (() => {
    if (guest.marketing_consent) {
      return { Icon: IconMailCheck, className: "text-green-600", label: t("guests.emailConsentYes") };
    }
    if (guest.marketing_consent_at) {
      return { Icon: IconMailOff, className: "text-red-500", label: t("guests.emailConsentUnsubscribed") };
    }
    return { Icon: IconMail, className: "text-slate-200", label: t("guests.emailConsentNo") };
  })();
  const EmailConsentIcon = emailConsent.Icon;

  return (
    <div className="inline-flex items-center gap-1.5 px-2 py-1 rounded-lg border border-slate-200 bg-white">
      {channel && (
        <Tooltip label={channel.label} withArrow>
          <span className="inline-flex items-center" aria-label={channel.label}>
            {channel.icon}
          </span>
        </Tooltip>
      )}
      <Tooltip label={hostAgain.label} withArrow>
        <span className="inline-flex items-center" aria-label={hostAgain.label}>
          <HostIcon size={16} className={hostAgain.className} />
        </span>
      </Tooltip>
      <Tooltip label={emailConsent.label} withArrow>
        <span className="inline-flex items-center" aria-label={emailConsent.label}>
          <EmailConsentIcon size={16} className={emailConsent.className} />
        </span>
      </Tooltip>
      {flag ? (
        <Tooltip label={nationalityName ?? ""} withArrow disabled={!nationalityName}>
          <span
            className="inline-flex items-center text-base leading-none"
            aria-label={nationalityName ?? undefined}
          >
            {flag}
          </span>
        </Tooltip>
      ) : (
        <span className="inline-flex items-center">
          <IconWorld size={16} className="text-slate-200" />
        </span>
      )}
    </div>
  );
}

export function GuestsTable({ guests, properties, onGuestClick, onBookingClick }: GuestsTableProps) {
  const { t } = useTranslation();
  const propertyMap = new Map(properties.map((p) => [p.id, p.name]));
  return (
    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200">
              <th
                className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider"
                style={{ width: 160 }}
              >
                {t("guests.columnProperty")}
              </th>
              <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                {t("guests.columnName")}
              </th>
              <th className="px-2.5 py-4 w-px whitespace-nowrap"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {guests.length === 0 && (
              <tr>
                <td colSpan={3} className="px-6 py-12 text-center text-sm text-slate-400">
                  {t("guests.noResultsFiltered")}
                </td>
              </tr>
            )}
            {guests.map((guest) => (
              <tr
                key={guest.id}
                role={onGuestClick ? "button" : undefined}
                tabIndex={onGuestClick ? 0 : undefined}
                onClick={onGuestClick ? () => onGuestClick(guest) : undefined}
                onKeyDown={
                  onGuestClick
                    ? (e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          onGuestClick(guest);
                        }
                      }
                    : undefined
                }
                className={`group hover:bg-slate-50 transition-colors ${
                  onGuestClick ? "cursor-pointer" : ""
                }`}
              >
                <td className="px-2.5 py-4" style={{ width: 160 }}>
                  <p className="font-semibold text-xs text-slate-900 truncate">
                    {propertyMap.get(guest.property_id) || guest.property_id.slice(0, 8)}
                  </p>
                  {onBookingClick ? (
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        onBookingClick(guest.booking_id);
                      }}
                      onKeyDown={(e) => e.stopPropagation()}
                      title={guest.channel_id ?? undefined}
                      style={{ fontSize: 10 }}
                      className="block max-w-[140px] truncate text-left text-slate-500 hover:text-slate-800 hover:underline cursor-pointer"
                    >
                      {guest.channel_id ? `#${guest.channel_id}` : "# -"}
                    </button>
                  ) : (
                    <p
                      className="text-xs text-slate-500 max-w-[140px] truncate"
                      title={guest.channel_id ?? undefined}
                    >
                      {guest.channel_id ? `#${guest.channel_id}` : "# -"}
                    </p>
                  )}
                </td>
                <td className="px-2.5 py-4">
                  <p className="font-medium text-xs text-slate-900">
                    {guest.first_name} {guest.last_name}
                  </p>
                </td>
                <td className="px-2 py-4">
                  <GuestStatusIcons guest={guest} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
