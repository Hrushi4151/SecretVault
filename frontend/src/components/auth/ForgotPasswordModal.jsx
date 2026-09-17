import React, { useState } from 'react';
import { Modal } from '../common/Modal';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { Mail, CheckCircle2 } from 'lucide-react';

export const ForgotPasswordModal = ({ isOpen, onClose }) => {
  const [email, setEmail] = useState('');
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !email.includes('@')) {
      setError('Please provide a valid account email address.');
      return;
    }

    setIsLoading(true);
    setError(null);

    setTimeout(() => {
      setIsLoading(false);
      setIsSubmitted(true);
    }, 600);
  };

  const handleReset = () => {
    setEmail('');
    setIsSubmitted(false);
    setError(null);
    onClose();
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleReset}
      title="Reset Password"
      description="Enter your registered email address to receive password recovery instructions."
      maxWidth="md"
    >
      {isSubmitted ? (
        <div className="flex flex-col items-center text-center py-4 gap-4 font-body text-white">
          <div className="w-12 h-12 rounded-2xl bg-[#3F0016] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/20">
            <CheckCircle2 className="w-6 h-6" />
          </div>
          <div className="flex flex-col gap-1">
            <h4 className="text-sm font-semibold text-white font-headline">Recovery Dispatched</h4>
            <p className="text-xs text-[#F4B5C8] max-w-xs">
              If an account is associated with <span className="font-mono text-white font-bold">{email}</span>, password reset instructions have been transmitted.
            </p>
          </div>
          <Button variant="secondary" onClick={handleReset} className="w-full mt-2">
            Return to Sign In
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4 text-white">
          {error && <Alert variant="danger" message={error} onDismiss={() => setError(null)} />}

          <Input
            label="Email Address"
            type="email"
            placeholder="developer@company.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            leftIcon={<Mail className="w-4 h-4" />}
            required
            autoFocus
          />

          <div className="flex items-center justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={handleReset}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" isLoading={isLoading}>
              Send Instructions
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
};
