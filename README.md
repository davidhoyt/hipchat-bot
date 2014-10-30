hipchat-bot
===========

General purpose HipChat (XMPP) bot using Akka, RxScala, and Smack and includes a Scala REPL bot for fun.

See `hipchat.conf` for details on how to configure it. For HipChat, you'll need access to 
the XMPP/Jabber configuration available in the administrative section of your account.

You'll need:
- A dedicated user account with username and password
- The user's XMPP JID
  - You may need to login under the bot's account settings in order to find this
- The user's mention name (e.g. `@foobar`)
- The user's nickname (e.g. `Foo Bar`)
- One or more rooms' XMPP JID

You should then edit `hipchat.conf` and set the values accordingly.

The main class for running this is `com.github.davidhoyt.hipchat.Main`.

See `com.github.davidhoyt.hipchat.scalabot.ScalaBot` for details on how to build your own bot.
