-- Which alarms a wearer's followers get (You -> Sharing -> Alarms your followers get). The app always
-- adds urgent low. Followers already read the profile rows of the people they follow
-- ("profiles: me and my people"), and only the owner can write theirs ("profiles: update mine").
-- Until this runs, the app treats everyone as sharing every alarm.
alter table public.profiles
  add column if not exists shared_alarms text[] not null
  default array['URGENT_LOW', 'LOW', 'GOING_LOW', 'HIGH', 'SIGNAL_LOSS'];
