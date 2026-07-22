New in 1.8.3
- Available version number fetch retries -- initial fetch from github occasionally fails

New in 1.8.2
- stricter enforcement of 1 machine limit for tracker (--ha=false)

New in 1.8.1
- Restart on import without manual intervention

New in 1.8.0
- Update key generation with separate features to set secrets and restart the apps
- Fixed the stop behavior to correctly set the scaling to 0
- Differentiate correctly absent machines from existing suspended ones
- Correctly compare version numbers.

Built as fly.io Docker container and released at
https://owlcms-cloud.fly.dev