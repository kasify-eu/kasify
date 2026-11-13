import { IconCheck, IconMail, IconPhone, IconWorld, IconX } from "@tabler/icons-react";
import type { Guest } from "~/types/bookings";
import { useTranslation } from "react-i18next";
import { ChannelBadge } from "~/components/bookings/booking-utils";

interface GuestCardListProps {
  guests: Guest[];
  onGuestClick?: (guest: Guest) => void;
  onBookingClick?: (bookingId: string) => void;
}

export function GuestCardList({ guests, onGuestClick, onBookingClick }: GuestCardListProps) {
  const { t, i18n } = useTranslation();
  const isEs = i18n.language.startsWith("es");
  if (guests.length === 0) {
    return (
      <p className="py-12 text-center text-sm text-slate-400">
        {t("guests.noResultsFiltered")}
      </p>
    );
  }

  return (
    <div className="space-y-3">
      {guests.map((guest) => (
        <div
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
          className={`bg-white rounded-xl p-4 shadow-sm border border-slate-100 ${
            onGuestClick ? "cursor-pointer hover:border-slate-300 transition-colors" : ""
          }`}
        >
          <p className="text-base font-bold text-slate-900">
            {guest.first_name} {guest.last_name}
          </p>
          <div className="flex items-center gap-2 mt-1">
            {guest.channel ? <ChannelBadge channel={guest.channel} /> : null}
            {onBookingClick ? (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onBookingClick(guest.booking_id);
                }}
                onKeyDown={(e) => e.stopPropagation()}
                style={{ fontSize: 10 }}
                className="font-medium font-mono text-slate-500 hover:text-slate-800 hover:underline cursor-pointer"
              >
                {guest.channel_id || `#${guest.booking_id.slice(0, 8)}`}
              </button>
            ) : (
              <span className="text-xs font-medium font-mono text-slate-500">
                {guest.channel_id || `#${guest.booking_id.slice(0, 8)}`}
              </span>
            )}
          </div>
          <div className="mt-3 space-y-1.5">
            {guest.would_host_again === true && (
              <div className="flex items-center gap-2 text-sm font-semibold text-emerald-700">
                <IconCheck size={16} />
                <span>{t("guests.wouldHostAgainYes")}</span>
              </div>
            )}
            {guest.would_host_again === false && (
              <div className="flex items-center gap-2 text-sm font-semibold text-rose-600">
                <IconX size={16} />
                <span>{t("guests.wouldHostAgainNo")}</span>
              </div>
            )}
            {guest.nationality && (
              <div className="flex items-center gap-2 text-sm text-slate-600">
                <IconWorld size={16} className="text-slate-400" />
                <span>{isEs ? guest.nationality.name_es : guest.nationality.name}</span>
              </div>
            )}
            {guest.email && (
              <div className="flex items-center gap-2 text-sm text-slate-600">
                <IconMail size={16} className="text-slate-400" />
                <span>{guest.email}</span>
              </div>
            )}
            {guest.phone && (
              <div className="flex items-center gap-2 text-sm text-slate-600">
                <IconPhone size={16} className="text-slate-400" />
                <span>
                  {guest.phone_prefix ? `${guest.phone_prefix} ` : ""}{guest.phone}
                </span>
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
