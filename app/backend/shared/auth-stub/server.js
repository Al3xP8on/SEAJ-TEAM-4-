const express = require('express');
const jwt = require('jsonwebtoken');

const app = express();
app.use(express.json());

const SECRET = process.env.JWT_SECRET || 'changeme';

app.post('/token', (req, res) => {
  const { sub = 'stub-user', roles = ['USER'] } = req.body;
  const token = jwt.sign({ sub, roles }, SECRET, { algorithm: 'HS256', expiresIn: '1h' });
  res.json({ token });
});

app.listen(3000, () => console.log('auth-stub listening on 3000'));
