export type PropertyInvitation = {
    id: string;
    property_id: string;
    email: string | null;
    token: string;
    max_uses: number | null;
    uses: number;
    expires_at: string | null;
    created_at: string;
    accepted_at: string | null;
};

export type CrewMember = {
    id: string;
    name: string;
    last_name: string;
    email: string;
    service: string | null;
    access_level: string;
};

export type PropertyInvitationsResponse = {
    invitations: PropertyInvitation[];
};

export type CrewMembersResponse = {
    crew: CrewMember[];
    default_cleaner_id?: string;
};

export type PropertyAccessLevel = "manager" | "staff" | "contractor";

export type CreatePropertyInvitationRequest = {
    email?: string;
    maxUses?: number;
    expiresAt?: string;
    accessLevel?: PropertyAccessLevel;
    locale?: "en" | "es";
};

export type AcceptInvitationResponse = {
    propertyId: string;
    propertyName: string;
};
