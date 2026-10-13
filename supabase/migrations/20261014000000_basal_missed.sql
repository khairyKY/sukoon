-- Missed long-acting for followers (You -> Insulin -> Long-acting reminder -> Tell my followers).
-- The wearer's phone sets it to when the dose was due, 2 hours after, if it isn't logged, and clears
-- it when it is. Followers already read the profiles of the people they follow, and only the owner
-- writes theirs, so the existing policies cover it. Until this runs, the app simply sends nothing.
alter table public.profiles
  add column if not exists basal_missed_since timestamptz;
