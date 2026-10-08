-- Delete my account: the signed-in person removes themselves, and with them (on delete cascade) their
-- profile, readings, follows both ways and invites. Called from the app (You → People) and the
-- follow page. Required by Google Play for apps with accounts.
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Not signed in';
  end if;
  delete from auth.users where id = auth.uid();
end $$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
