-- Phase 2A: Routing Completeness
-- Track matchmaking offer rounds to support multi-round re-routing and admin escalation capping

ALTER TABLE routing_offers ADD COLUMN IF NOT EXISTS round INTEGER NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS routing_offers_pickup_round_idx ON routing_offers (pickup_id, round);
