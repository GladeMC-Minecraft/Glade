// Hermes: nimmt Upload-Anfragen von Glade-Servern entgegen.
//
// Ablauf:
//   1. Das Plugin sendet POST { sha1, size } mit dem Header "x-hermes-key".
//   2. Die Function prüft Key, Dateigröße und Tageslimit.
//   3. Sie räumt alte Packs des Keys auf und gibt zurück:
//        { publicUrl, uploadUrl }   (uploadUrl ist null, wenn die Datei schon existiert)
//   4. Das Plugin lädt die Zip per PUT an uploadUrl hoch.
//
// Deployment (ohne JWT-Prüfung, die Authentifizierung läuft über den Hermes-Key):
//   supabase functions deploy hermes-upload --no-verify-jwt

// Die Fehler, die ein Editor ohne Deno-Unterstützung hier anzeigt (z. B. "Cannot find name 'Deno'"),
// betreffen nur die Anzeige. Beim Deployen auf Supabase spielen sie keine Rolle.
// @ts-nocheck

import { createClient } from "npm:@supabase/supabase-js@2";

const BUCKET = "hermes-packs";
const SHA1_PATTERN = /^[0-9a-f]{40}$/;

// WICHTIG: Hier stehen die NAMEN der Umgebungsvariablen, nie die Werte selbst.
// SUPABASE_URL und SUPABASE_SERVICE_ROLE_KEY stellt Supabase jeder Edge Function automatisch bereit.
// Falls du den neuen "Secret key" (sb_secret_...) nutzen willst, lege ihn im Dashboard unter
// Edge Functions -> Secrets als HERMES_SECRET_KEY an, dann hat er Vorrang.
const supabaseUrl = Deno.env.get("https://zpdnbxgqdfdkwulmkacr.supabase.co/functions/v1/hermes-upload");
const serviceKey = Deno.env.get("HERMES_SECRET_KEY") ?? Deno.env.get("sb_secret_EJ4mZQsrYqqDo-z32dgAeA_bS2youxi");
if (!supabaseUrl || !serviceKey) {
    throw new Error("Missing SUPABASE_URL or service key");
}

const supabase = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
    });
}

async function sha256Hex(text: string): Promise<string> {
    const data = new TextEncoder().encode(text);
    const hash = await crypto.subtle.digest("SHA-256", data);
    return Array.from(new Uint8Array(hash))
        .map((byte) => byte.toString(16).padStart(2, "0"))
        .join("");
}

Deno.serve(async (req: Request) => {
    if (req.method !== "POST") {
        return json({ error: "Method not allowed" }, 405);
    }

    // --- Key prüfen ---
    const key = req.headers.get("x-hermes-key")?.trim();
    if (!key) {
        return json({ error: "Missing Hermes key" }, 401);
    }

    const { data: keyRow, error: keyError } = await supabase
        .from("hermes_keys")
        .select("id, max_file_bytes, max_uploads_per_day, active")
        .eq("key_hash", await sha256Hex(key))
        .maybeSingle();

    if (keyError) {
        console.error("key lookup failed", keyError);
        return json({ error: "Internal error" }, 500);
    }
    if (!keyRow || !keyRow.active) {
        return json({ error: "Invalid Hermes key" }, 401);
    }

    // --- Anfrage prüfen ---
    let body: { sha1?: unknown; size?: unknown };
    try {
        body = await req.json();
    } catch {
        return json({ error: "Invalid JSON" }, 400);
    }

    const sha1 = String(body.sha1 ?? "").toLowerCase();
    const size = Number(body.size);

    if (!SHA1_PATTERN.test(sha1)) {
        return json({ error: "Invalid sha1" }, 400);
    }
    if (!Number.isFinite(size) || size <= 0) {
        return json({ error: "Invalid size" }, 400);
    }
    if (size > keyRow.max_file_bytes) {
        const maxMb = Math.floor(keyRow.max_file_bytes / 1048576);
        return json({ error: `File too large (max ${maxMb} MB)` }, 413);
    }

    const storage = supabase.storage.from(BUCKET);
    const folder = keyRow.id as string;
    const path = `${folder}/${sha1}.zip`;
    const publicUrl = storage.getPublicUrl(path).data.publicUrl;

    // --- Vorhandene Dateien dieses Keys (neueste zuerst) ---
    const { data: files, error: listError } = await storage.list(folder, {
        limit: 100,
        sortBy: { column: "created_at", order: "desc" },
    });
    if (listError) {
        console.error("list failed", listError);
        return json({ error: "Internal error" }, 500);
    }
    const existing = (files ?? []).filter((file: { name: string }) => file.name.endsWith(".zip"));

    // Gleiche Datei schon vorhanden: nichts hochladen, URL zurückgeben
    if (existing.some((file: { name: string }) => file.name === `${sha1}.zip`)) {
        return json({ publicUrl, uploadUrl: null });
    }

    // --- Tageslimit ---
    const since = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
    const { count, error: countError } = await supabase
        .from("hermes_uploads")
        .select("id", { count: "exact", head: true })
        .eq("key_id", keyRow.id)
        .gte("created_at", since);

    if (countError) {
        console.error("count failed", countError);
        return json({ error: "Internal error" }, 500);
    }
    if ((count ?? 0) >= keyRow.max_uploads_per_day) {
        return json({ error: "Daily upload limit reached" }, 429);
    }

    // --- Aufräumen: nur die neueste alte Version behalten (die neue kommt gleich dazu) ---
    const outdated = existing.slice(1).map((file: { name: string }) => `${folder}/${file.name}`);
    if (outdated.length > 0) {
        const { error: removeError } = await storage.remove(outdated);
        if (removeError) {
            console.error("cleanup failed", removeError);
        }
    }

    // --- Upload-URL erzeugen ---
    const { data: signed, error: signError } = await storage.createSignedUploadUrl(path, {
        upsert: true,
    });
    if (signError || !signed) {
        console.error("sign failed", signError);
        return json({ error: "Internal error" }, 500);
    }

    await supabase.from("hermes_uploads").insert({
        key_id: keyRow.id,
        sha1,
        bytes: size,
    });

    return json({ publicUrl, uploadUrl: signed.signedUrl });
});