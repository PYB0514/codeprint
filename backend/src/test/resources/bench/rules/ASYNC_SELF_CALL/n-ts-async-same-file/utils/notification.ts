async function sendEmail(msg: string) {
}

async function notify(msg: string) {
  await sendEmail(msg);
}
