
if (!session) {
  session = request.getSession(true);
}

if (!session.counter) {
  session.counter = 1
}

println """
<html>
    <head>
        <title>Groovy Servlet</title>
    </head>
    <body>
Hello, ${request.remoteHost}: ${session.counter}! ${new Date()}
    </body>
</html>
"""
session.counter = session.counter + 1