-- ============================================================================
-- Phase 8 Migration: Knowledge Base field additions
-- Adds published_at and content columns to knowledge_articles table
-- ============================================================================

ALTER TABLE knowledge_articles ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE knowledge_articles ADD COLUMN content TEXT;
