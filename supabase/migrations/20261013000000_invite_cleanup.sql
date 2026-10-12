-- Spent invites: every invite runs out 24 hours after it's made, used or not, and the follow it
-- created lives in public.follows, so an invite row has no use a week after it expired. This clears
-- them once now and then daily at 03:17 UTC with pg_cron. Safe to run again: the job is replaced.
-- Without pg_cron (Database -> Extensions -> pg_cron, or the error notice below), the one-off
-- clean-up still runs; turn pg_cron on and run this file again to get the daily job.

delete from public.invites where expires_at < now() - interval '7 days';

do $$
begin
  create extension if not exists pg_cron;
  perform cron.unschedule(jobid) from cron.job where jobname = 'sukoon-invite-cleanup';
  perform cron.schedule(
    'sukoon-invite-cleanup',
    '17 3 * * *',
    $job$delete from public.invites where expires_at < now() - interval '7 days'$job$
  );
exception when others then
  raise notice 'pg_cron is not available (%); spent invites were cleared once, not scheduled.', sqlerrm;
end $$;
