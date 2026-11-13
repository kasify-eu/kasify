import { createContext } from "react-router";
import type { SessionUser } from "~/types/user";

export const userContext =
    createContext<SessionUser | null>(null);