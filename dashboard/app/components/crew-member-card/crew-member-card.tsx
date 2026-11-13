import {IconBrush, IconHome, IconSwimming, IconTool, IconTrash, IconUser,} from "@tabler/icons-react";
import type {PropertyMember} from "~/types/properties";

interface CrewMemberCardProps {
    crewMember: PropertyMember;
    onRemove?: () => void;
}

const getRoleIcon = (role: string) => {
    const normalizedRole = role.toLowerCase();
    if (normalizedRole.includes("clean")) return IconBrush;
    if (normalizedRole.includes("maintenance") || normalizedRole.includes("repair"))
        return IconTool;
    if (normalizedRole.includes("pool")) return IconSwimming;
    if (normalizedRole.includes("property") || normalizedRole.includes("manager"))
        return IconHome;
    return IconUser;
};

export function CrewMemberCard({crewMember, onRemove}: CrewMemberCardProps) {
    const RoleIcon = getRoleIcon(crewMember.service ?? crewMember.access_level);
    const fullName = [crewMember.name, crewMember.last_name].filter(Boolean).join(" ");
    const isOwner = crewMember.access_level.toLowerCase() === "owner";
    const showRemove = onRemove !== undefined && !isOwner;

    return (
        <div
            className="relative bg-white  rounded-3xl p-4 pl-8 pr-8 pb-8 border border-slate-100  hover:shadow-2xl hover:shadow-slate-200/50 transition-all duration-300">
            <div className="flex items-center justify-between min-h-8">
                <div className="flex items-center gap-1.5 text-slate-400">
                    <RoleIcon size={16} stroke={1.5}/>
                    <p className="text-[10px] font-semibold uppercase tracking-wider">
                        {crewMember.service ?? crewMember.access_level}
                    </p>
                </div>
                {showRemove && (
                    <button
                        type="button"
                        onClick={onRemove}
                        aria-label="Remove team member"
                        className="-mr-4 w-10 h-10 rounded-full flex items-center justify-center text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors"
                    >
                        <IconTrash size={20} stroke={1.5}/>
                    </button>
                )}
            </div>
            <h3 className="font-bold text-lg text-slate-900 mt-5">
                {fullName}
            </h3>
            <p className="text-slate-400/80 text-[11px] font-medium mt-0.5">
                {crewMember.email}
            </p>
        </div>
    );
}