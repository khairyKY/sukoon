-- Finger-pricks reach followers: a reading with trend 9 is a finger-prick (no arrow), shared while
-- the wearer has no sensor. Older Sukoon versions and the follow page treat it as a plain reading.
alter table public.readings drop constraint if exists readings_trend_check;
alter table public.readings add constraint readings_trend_check check (trend between -2 and 2 or trend = 9);
